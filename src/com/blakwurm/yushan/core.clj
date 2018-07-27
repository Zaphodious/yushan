(ns com.blakwurm.yushan.core
  (:require [yada.yada :as yada]
            [schema.core :as schema]
            [clojure.test.check.generators]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.lytek.spec]))

(defonce *db (atom {}))

(defn filter-data [params]
  (let []))

(defn reset-db []
  (reset! *db (->> (gen/sample (s/gen :lytek/solar))
                   (map (fn [a] {(:id a) a}))
                   (into {}))))


(defn api-read [request]
  (let [{:keys [character-type id owner category] :as params}
        (:query (:parameters request))]
    (println "request is " request)
    {:resp 0
     :params params
     :data (vals @*db)
     ;(str (:query (:parameters request)))
     :error ""}))

(defn api-create [request]
  {:resp 0 :data [] :error ""})

(defn api-update [request]
  {:resp 0 :data [] :error ""})

(defn api-delete [request]
  {:resp 0 :data [] :error ""})

(defn optional [a] (schema/optional-key a))
(defn required [a] a)

(def query-params
  {(optional :character-type) String
   (optional :id) String
   (optional :owner) String
   (optional :results) Number
   (optional :category) String})

(def api-v1-resource
  (yada/resource
    {:produces "application/json"
     :methods {:get #'api-read
               :put #'api-update
               :post #'api-create
               :delete #'api-delete}
     :parameters {:query query-params}}))

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