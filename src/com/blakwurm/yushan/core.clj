(ns com.blakwurm.yushan.core
  (:require [yada.yada :as yada]
            [schema.core :as schema]
            [clojure.test.check.generators]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.lytek.spec]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [honeysql.core :as hsql]
            [honeysql.helpers :as hsql.help])
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
  {:subcategory {:opt? optional :prismatic-type String :write-as name :read-as keyword :param-type :query-filter :row-type :text}
   :name        {:opt? optional :prismatic-type String :param-type :query-filter :row-type :text}
   :id          {:opt? optional :prismatic-type String :param-type :query-filter :row-type :int :sql-extra [:primary :key]}
   :category    {:opt? optional :prismatic-type String :write-as name :read-as keyword :param-type :query-filter :row-type :text}
   :supernal    {:opt? optional :prismatic-type Keyword :param-type :query-filter}
   :perpage     {:opt? optional :prismatic-type Number :param-type :query-modifier}
   :pagenumber  {:opt? optional :prismatic-type Number :param-type :query-modifier}
   :description {:param-type :row :row-type :text}
   :rest        {:param-type :row :row-type :text}})

(def test-query
  {:subcategory "dawn"
   :name "Mubaraka"})

(defn params-to-db-rows [qp's]
  (->> qp's
       (map (fn [[k v]] (assoc v :key k)))
       (filter :row-type)
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

(defn put-in-db [prepped-entity]
  (try
    (jdbc/insert! db-connection :entities prepped-entity)
    (catch Exception e
      (jdbc/update! db-connection :entities prepped-entity ["id = ?" (:id prepped-entity)]))))

(def write-to-chan (async/chan 10000 (map #(prep-entity-for-insertion query-params %))))

(defn kickoff-writer-go-block [transduced-chan]
  (async/go-loop []
    (put-in-db (async/<!! transduced-chan))
    (recur)))
(kickoff-writer-go-block write-to-chan)

(defn write-entity! [entity]
  (async/go (async/>!! write-to-chan entity)))

(def test-honey-query
  {:select [:*]
   :limit 10
   :offset 10
   :from   [:entities]
   :where  [:and [:like :rest "%:supernal :athletics%"]]})
            ;[:like :rest "%:martial-arts 3%"]
            ;[:like :rest "%:wits 3%"]]})



(defn insert-test-entity []
  (try
    (write-entity! (gen/generate (s/gen :lytek/solar)))
    (catch Exception e
      e)))

(defn insert-some-test-entities! []
  (async/go (dotimes [n 100000] (insert-test-entity))))

(defn create-db []
  (try (jdbc/db-do-commands db-connection
                            (jdbc/create-table-ddl :entities
                                                   (params-to-db-rows query-params)))
       (catch Exception e (println "Table note created. " e))))

(defn drop-entities! []
  (jdbc/db-do-commands db-connection (jdbc/drop-table-ddl :entities)))


(defn params-to-prismatic [qp's]
  (into {}
        (map (fn [[query-key {:keys [opt? prismatic-type param-type]}]]
               (if opt?
                 [(opt? query-key) prismatic-type]))
             qp's)))

(defn params-by-param-type [qp's]
  (reduce (fn [ds [query-key {:keys [opt? prismatic-type param-type]}]]
            (assoc ds param-type (conj (or (get ds param-type) []) query-key)))
          {}
          qp's))

(defn params-to-honey-query [qp's params]
  (let [{:keys [query-filter]} (params-by-param-type qp's)
        target-map (select-keys params query-filter)
        subfilter-keys (keys target-map)]
    (println "params are " params)
    (merge
      {:select [:*]
       :from [:entities]
       :limit (or (:perpage params) 10)
       :offset (* (or (:perpage params) 10)
                  (min 0 (dec (or (:pagenumber params) 1))))}
      (if (empty? target-map)
        {}
        {:where (into [:and]
                      (map (fn [[k v]]
                             ((if (:row-type (k qp's)) sql-transform-basic sql-transform-rest) [k v]))
                           target-map))}))))

(defn read-entities [qp's params]
  (into []
        (map #(hydrate-entity-after-selection query-params %)
             (jdbc/query db-connection (hsql/format (params-to-honey-query qp's params))))))

(defn api-read [request]
  (let [{:keys [] :as params} (:query (:parameters request))]
    {:resp 0
     :params params
     :data (read-entities query-params
                          (:query (:parameters request)))
     :error ""}))

(defn api-create [request]
  {:resp 0 :data [] :error ""})

(defn api-update [request]
  {:resp 0 :data [] :error ""})

(defn api-delete [request]
  {:resp 0 :data [] :error ""})


(def api-v1-resource
  (yada/resource
    {:produces   "application/json"

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
            ["/api/" {"asdf/v1/" api-v1-resource}]
            {:port 3000})))

(defn stop-server []
  ((:close @*server)))

(defn restart-server []
  (do
    (stop-server)
    (start-server)))