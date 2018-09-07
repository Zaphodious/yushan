(ns com.blakwurm.yushan.db
  (:require [clojure.java.jdbc :as jdbc]
            [honeysql.core :as honey]
            [honeysql.helpers :as honey.help]
            [clojure.core.async :as async]))

(def db-connection
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "db/database.db"})

(def --db-access-chan (async/chan 1000))
(def *--access-go (atom nil))

(defn --init-db-access-go [chan]
  (async/go-loop []
    (let [{:keys [access-fn return-chan]} (async/<! chan)
          write-result (try (access-fn)
                            (catch Exception e
                              (do
                               (println "Exception in writer thread")
                               (println e)
                               false)))]
      (async/>! return-chan write-result))
    (recur)))
(reset! *--access-go (--init-db-access-go --db-access-chan))

(defn make-query-from-map
  "Takes a regular map, and convers it into a honey 'for' clause"
  [thingmap]
  (->> thingmap
       (map (fn [[k v]] [:= k v]))
       (into [:and])))

(defn --make-full-query [{:keys [table query page count] :or {page 0, count 10}}]
  (let [query-vec (if (vector? query) query
                                      (make-query-from-map query))
        no-where-map {:select [:*]
                      :from   [table]
                      :limit count
                      :offset (* page count)}
        where-map (if (empty? query)
                    {}
                    {:where query-vec})]
    (honey/format (into no-where-map where-map))))

(defn --access
  "Private function providing access to the DB. Takes a lambda, executes it on the coordinated db thread and returns
  the result synchronously. If any exceptions are thrown at any point, returns false."
  [access-fn]
  (try
    (let [return-chan (async/promise-chan)]
      (async/go (async/>! --db-access-chan {:access-fn access-fn :return-chan return-chan}))
      (async/<!! return-chan))
    (catch Exception e
      (do
       (println "Exception in access fn")
       (println e)
       false))))

(defn --insert [{:keys [table transform-fn thing] :or {transform-fn identity}}]
  (try
    (jdbc/insert! db-connection table (transform-fn thing))
    (catch Exception e
      false)))

(defn --update [{:keys [table transform-fn thing] :or {transform-fn identity}}]
  (try
    (jdbc/update! db-connection table (transform-fn thing) ["id = ?" (:id thing)])
    (catch Exception e
      false)))

(defn --delete [{:keys [table id]}]
  (try
    (jdbc/delete! db-connection table ["id = ?" id])
    (catch Exception e
      false)))

(defn --read [{:keys [table query count page transform-fn] :as param-map :or {transform-fn identity}}]
  (try
    (map transform-fn (jdbc/query db-connection (--make-full-query param-map)))
    (catch Exception e
      false)))

(defn map-to-column-spec
  "Takes a map {:columnName [:jdbc :spec :definition]...} and converts it to the proper vector format that
  jdbc requires."
  [column-map]
  (->> column-map
       (map (fn [[k v]] (into [k] v)))
       (into [])))

(defn --make-table [{:keys [table column-info]}]
  (jdbc/db-do-commands db-connection (jdbc/create-table-ddl table (if (map? column-info)
                                                                    (map-to-column-spec column-info)
                                                                    column-info))))

(defn --drop-table [{:keys [table-name]}]
  (jdbc/db-do-commands db-connection (jdbc/drop-table-ddl table-name)))

(defn read-many
  "Takes a param map, including a transform-fn which should prepare the stored entity for viewing. Defaults to
  'identity'. Same applies to any function with 'transform-fn'."
  [{:keys [table query transform-fn count page] :as params}]
  (--access #(--read params)))

(defn read-one [{:keys [table query transform-fn] :as params}]
  (first (read-many (assoc params :page 0 :count 1))))

(defn insert-one [{:keys [table transform-fn thing] :as params}]
  (first (--access #(--insert params))))

(defn update-one [{:keys [table transform-fn thing] :as params}]
  (first (--access #(--update params))))

(defn delete-one [{:keys [table id] :as params}]
  (first (--access #(--delete params))))

(defn make-table [{:keys [table column-info] :as params}]
  (--access #(--make-table params)))

(defn drop-table [{:keys [table] :as params}]
  (--access #(--drop-table params)))
