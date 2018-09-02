(ns com.blakwurm.yushan.core-old
  (:require [yada.yada :as yada]
            [yada.resources.file-resource :as yada.file]
            [bidi.bidi :as bidi]
            [bidi.ring]
            [schema.core :as schema]
            [clojure.test.check.generators]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [honeysql.core :as hsql]
            [honeysql.helpers :as hsql.help]
            [spec-coerce.core :as sc]
            [clojure.tools.namespace.repl :as namespace.repl])
  (:import (clojure.lang Keyword)))

(def db-connection
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "db/database.db"})


(defn optional [a] (schema/optional-key a))
(defn required [a] a)

(defn sql-transform-basic [[k v]]
  [:= k v])
(defn sql-transform-rest [[k v]]
  [:like :rest (str "%" k " " v "%")])

(def query-params
  {:subcategory {:opt? optional :prismatic-type String :write-as name :read-as keyword :param-type :query-filter :row-type :text :table :entities}
   :mode        {:opt? optional :prismatic-type String :write-as name :read-as keyword :param-type :query-filter}
   :name        {:opt? optional :prismatic-type String :param-type :query-filter :row-type :text :table :entities}
   :id          {:opt? optional :prismatic-type String :param-type :query-filter :row-type :text :sql-extra [:primary :key] :table :entities}
   :category    {:opt? optional :prismatic-type String :write-as name :read-as keyword :param-type :query-filter :row-type :text :table :entities}
   :owner       {:opt? optional :prismatic-type String :write-as name :param-type :query-filter :row-type :text :table :relationships}
   :property    {:opt? optional :prismatic-type String :write-as name :param-type :query-filter :row-type :text :table :relationships}
   :supernal    {:opt? optional :prismatic-type Keyword :param-type :query-filter} 
   :perpage     {:opt? optional :prismatic-type Number :param-type :query-modifier}
   :pagenumber  {:opt? optional :prismatic-type Number :param-type :query-modifier}
   :description {:param-type :row :row-type :text :table :entities}
   :rest        {:param-type :row :row-type :text :table :entities}})

(defn map->lytek-map [mappo]
  (into {}
        (map
          (fn [[k v]]
            {(if (qualified-keyword? k)
               k
               (keyword "lytek" (name k)))
             v})
          mappo)))

(defn lytek-map->map [lymappo]
  (into {}
        (map
          (fn [[k v]]
            {(keyword (name k)) v})
          lymappo)))

(defn coerce-entity [entity]
  (let [coerse-spec :lytek/entity]
    (-> entity
        map->lytek-map
        sc/coerce-structure
        lytek-map->map)))

(defn explain-entity-validity
  "Passes entity to spec/explain, using lyspec/get-applicable-spec-pre-coersion. Returns 'true' instead of nil"
  [entity]
  #_(or (::s/problems (s/explain-data (lyspec/get-applicable-spec-pre-coersion entity) entity))
        true)
  (nil? (s/explain-data :lytek/entity entity)))

(defn is-entity-valid?
  "Passes entity to spec/valid?, using lyspec/get-applicable-spec-per-coersion"
  [entity]
  (s/valid? :lytek/entity entity))

(defn group-by-validity
  "returns a map, with :to-insert holding entities fit for insertion, and :to-return holding either 'true' or a spec/explain result"
  [entity-seq]
  {:to-insert (filter is-entity-valid? entity-seq)
   :to-return (map explain-entity-validity entity-seq)})

(defn determine-mode [{:as params :keys [mode]}]
  (let [tables-referenced (->> params 
                               (map first)
                               (map #(get query-params %)) 
                               (map :table) 
                               (filter #(not (nil? %)))
                               sort)]
   (cond
     mode mode
     (not (empty? tables-referenced)) (first tables-referenced))))

(defn remove-non-mode-params [param-map]
  (let [query-mode (determine-mode param-map)]
    (->> param-map
        (filter (fn [[k v]] (= (:table (get query-params k)) query-mode))) 
        (into {:mode query-mode}))))
             
      

(def test-query
  {:subcategory "dawn"
   :name        "Mubaraka"})

(defn params-to-db-rows [qp's table-name-kv]
  (->> qp's
       (map (fn [[k v]] (assoc v :key k)))
       (filter :row-type)
       (filter (fn [{:keys [table]}] (= table table-name-kv)))
       (map (fn [{:as m :keys [key row-type sql-extra]}]
              (into [(str/replace (name key) "-" "_")
                     row-type]
                    (or sql-extra []))))
       (into [])))

(defn params-to-row-titles [qp's]
  (->> qp's
       (map (fn [[k v]] (assoc v :key k)))
       (filter :row-type)
       (map :key)))

(defn prep-entity-for-insertion [qp's entity]
  (let [row-titles (params-to-row-titles qp's)
        insertion-ent (select-keys entity row-titles)
        write-out-ent (into {}
                            (map (fn [[k v]] {k ((or (:write-as (k qp's)) (fn [a] a)) v)})
                                 insertion-ent))
        rest-ent (apply (partial dissoc entity) row-titles)
        rest-edn (pr-str rest-ent)]
    (assoc write-out-ent :rest rest-edn)))

(defn hydrate-entity-after-selection [qp's entity]
  (merge (into {}
               (map (fn [[k v]]
                      {k ((or (:read-as (k qp's)) (fn [a] a)) v)}))
               (dissoc entity :rest))
         (read-string (or (:rest entity) "{}"))))

(declare read-entities)

(defn put-in-db [{:as request-map :keys [entity-to-write return-chan write-type]}]
  (async/go
    (let [sanitize-return (fn [a] (if a true false))
          input-result (try (case write-type
                              :insert (jdbc/insert! db-connection :entities (prep-entity-for-insertion query-params entity-to-write))
                              :update (when-not (empty? (read-entities query-params {:id (:id entity-to-write)}))
                                        (jdbc/update! db-connection :entities (prep-entity-for-insertion query-params entity-to-write)
                                                      ["id = ?" (:id entity-to-write)]))
                              :delete (jdbc/delete! db-connection :entities ["id = ?" (:id entity-to-write)]))
                            (catch Exception e
                              false))]
      (async/>! return-chan (sanitize-return input-result)))))
 
(defonce write-to-chan (async/chan 10000))                      ;(map #(prep-entity-for-insertion query-params %))))

(defn kickoff-writer-go-block [chan]
  (async/go-loop []
    (let [{:as request-map
           :keys [entity-to-write return-chan write-type]} (async/<!! chan)]
      (when request-map
        (try (put-in-db request-map)
          (catch Exception e
            (println "error in writer go block")
            (println e)))
        (recur)))))
(defonce writer-go (kickoff-writer-go-block write-to-chan))

;(def write-request-example
;  {:write-type      :insert
;   :return-chan     (async/chan)
;   :entity-to-write (gen/generate (s/gen :lytek/solar))})

(defn write-entity!
  ([entity] (write-entity! entity :insert))

  ([entity write-type]
   (let [ret-chan (async/chan 3)]
     (try
       (async/go (async/>! write-to-chan {:write-type write-type :entity-to-write entity :return-chan ret-chan}))
       (catch Exception e
         (println "error in write-entity function")))
     ret-chan)))

(defn insert-entity! [entity]
  (write-entity! entity :insert))
(defn update-entity! [entity]
  (write-entity! entity :update))
(defn delete-entity! [entity]
  (write-entity! entity :delete))

(def test-honey-query
  {:select [:*]
   :limit  10
   :offset 10
   :from   [:entities]
   :where  [:and [:like :rest "%:supernal :athletics%"]]})
;[:like :rest "%:martial-arts 3%"]
;[:like :rest "%:wits 3%"]]})



(defn insert-test-entity []
  (try
    (write-entity! (gen/generate (s/gen :lytek/entity)))
    (catch Exception e
      e)))

(defn insert-some-test-entities! [amt]
  (async/go (dotimes [n amt] (insert-test-entity))))

(defn get-tables-used [qp's]
  (->> qp's
       (map second)
       (filter :table)
       (map :table)
       (set)))

(defn create-table [table-name-kw]
  (try (jdbc/db-do-commands db-connection
                            (jdbc/create-table-ddl table-name-kw 
                                                   (params-to-db-rows query-params table-name-kw)))
       (catch Exception e (println "Table not created. " e))))

(defn create-db []
  (->> query-params
       (get-tables-used)
       (map create-table)
       (into [])))
 

(defn drop-entities! []
  (jdbc/db-do-commands db-connection (jdbc/drop-table-ddl :entities)))

(defn get-blank-by-category [category]
  (s/conform (keyword "lytek" (name category)) (read-string (slurp (str "blanks/" (name category) ".edn")))))

(defn params-to-prismatic [qp's]
  (into {}
        (map (fn [[query-key {:keys [opt? prismatic-type param-type]}]]
               (if opt?
                 [(opt? query-key) prismatic-type]))
             qp's)))

(defn params-by-param-type [qp's table-name]
  (->> qp's 
    (filter (fn [[_ {:keys [table]}]] (= table-name table)))
    (reduce (fn [ds [query-key {:keys [opt? prismatic-type param-type]}]]
              (assoc ds param-type (conj (or (get ds param-type) []) query-key)))
            {})))

(defn params-to-honey-query [qp's params]
  (let [table-name (determine-mode params)
        {:keys [query-filter] :as params-by-type} (params-by-param-type qp's table-name)
        target-map (select-keys params query-filter)
        subfilter-keys (keys target-map)]
    (println "params-by-type are " params-by-type)
    (println "params are " params)
    (merge
      {:select [:*]
       :from   [table-name]
       :limit  (or (:perpage params) 10)
       :offset (* (or (:perpage params) 10)
                  (min 0 (dec (or (:pagenumber params) 1))))}
      (if (empty? target-map)
        {}
        {:where (into [:and]
                      (map (fn [[k v]]
                             ((if (:row-type (k qp's)) sql-transform-basic sql-transform-rest) [k v]))
                           target-map))}))))

(defn get-relationships-under [owner-id]
  (jdbc/query db-connection (hsql/format ({:select :property 
                                           :from :relationships
                                           :where [:= :f.owner owner-id]}))))

(defn read-entities [qp's params]
  (into []
        (map #(hydrate-entity-after-selection query-params %)
             (jdbc/query db-connection (hsql/format (params-to-honey-query qp's (assoc params :mode :entities)))))))

(defmulti update-record determine-mode)

(defmethod update-record :entities
  [entity]
  :blabla)

(defmethod update-record :relationships
  [relationship]
  :hohoho)

(defn dispatch-params [qp's request mode-handlers]
  (let [{:as params :keys [mode category owner]} (:query (:parameters request))
        handler (get mode-handlers (or (:mode)))])) 

(defn api-read [request]
  (let [{:keys [] :as params} (:query (:parameters request))]
    {:resp   0
     :params params
     :data   (read-entities query-params
                            (:query (:parameters request)))
     :error  ""}))

(defn api-create [request]
  (let [template (get-blank-by-category "solar")
        {:keys [category subcategory] :as params} (:query (:parameters request))
        make-possible-ids (repeatedly (fn [] (gen/generate (s/gen :lytek/id))))
        try-insert #(map (fn [a] [a (insert-entity! (assoc template :id a))]) %)
        get-insert-result #(map (fn [[a ch]] (when (async/<!! ch) a)) %)
        get-only-successes #(filter identity %)
        used-id (-> make-possible-ids try-insert get-insert-result get-only-successes first)
        new-entity (first (read-entities query-params {:id used-id}))]
    {:resp 0 :data [new-entity] :error ""}))




(defonce *update (atom {}))

(defn api-update-relationships [request])

(defn api-update-entities [request]
  (reset! *update (:data (:body request)))
  (let [coerced-entities (map coerce-entity (-> request :body :data))
        merged-entities (map (fn [a] (merge (first (read-entities query-params {:id (:id a)})) a)) 
                             coerced-entities)
        {:keys [to-insert to-return]} (group-by-validity merged-entities)]
    (println (map :id merged-entities))
    ;(println to-return)
    (into [] (map update-entity! to-insert))
    {:resp  (if (reduce #(= true %1 %2) to-return)
              0 1)
     :data  to-return
     :error ""}))

(defn api-update [request]
  (let [op-mode :entities]
    (api-update-entities request)))

(defn api-delete [request]
  (println "deleting " (map :id (-> request :body :data)))
  (let [entities-to-delete (-> request :body :data)
        deletion-results (map delete-entity! entities-to-delete)
        resolved-deletion-results (map async/<!! deletion-results)]
    {:resp 0 :data resolved-deletion-results
     :error ""}))


(def api-v1-resource
  (yada/resource
    {:produces   "application/json"
     :consumes   "application/json"
     :methods    {:get    #'api-read
                  :put    #'api-update
                  :post   #'api-create
                  :delete #'api-delete}
     :parameters {:query (params-to-prismatic query-params)}}))

(defonce *server
         (atom {}))

(defn start-server []
  (reset! *server
          (yada/listener
            ["/"
             [["api/" {"asdf/v1/" api-v1-resource
                        "asdf/v1"  api-v1-resource}]
              ;["css/" (yada/yada (clojure.java.io/file "/public/css/"))]
              ["" (yada/yada (clojure.java.io/file "public/index.html"))]
              ["css/" (yada/yada (clojure.java.io/file "public/css"))]
              ["js/" (yada/yada (clojure.java.io/file "public/js"))]
              ["img/" (yada/yada (clojure.java.io/file "public/img"))]]]
            {:port 3000})))

(println "Prove it")

(defn stop-server []
  (when-let [close-fn (:close @*server)]
    (close-fn)))

(defn restart-server []
  (do
    (stop-server)
    (start-server)))

(defn reincarnate! []
  (async/close! write-to-chan)
  (stop-server)
  (namespace.repl/refresh-all)
  (com.blakwurm.yushan.core/start-server))