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
          write-result (access-fn)]
      (async/>! return-chan write-result))
    (recur)))
(reset! *--access-go (--init-db-access-go --db-access-chan))

(defn make-from-query-from-map [thingmap]
  (->> thingmap
       (map (fn [[k v]] [:= k v]))
       (into [:and])))

(defn --make-full-query [{:keys [table query page count] :or {page 0, count 10}}]
  (let [query-vec (if (vector? query) query
                      (make-from-query-from-map query))]
    (honey/format {:select [:*]
                   :from   [table]
                   :limit count
                   :offset (* page count)
                   :where  query-vec})))

(defn --access [access-fn]
  (let [return-chan (async/promise-chan)]
    (async/go (async/>! --db-access-chan {:access-fn access-fn :return-chan return-chan}))
    (async/<!! return-chan)))

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
    (transform-fn (jdbc/query db-connection (--make-full-query param-map)))
    (catch Exception e
      false)))

(defn read-many [{:keys [table query transform-fn count page] :as params}]
  (--access #(--read params)))

(defn read-one [{:keys [table query transform-fn] :as params}]
  (first (read-many (assoc params :page 0 :count 1))))

(defn insert-one [{:keys [table transform-fn thing] :as params}]
  (first (--access #(--insert params))))

(defn update-one [{:keys [table transform-fn thing] :as params}]
  (first (--access #(--update params))))

(defn delete-one [{:keys [table id] :as params}]
  (first (--access #(--delete params))))

