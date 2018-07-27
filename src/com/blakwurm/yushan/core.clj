(ns com.blakwurm.yushan.core
  (:require [yada.yada :as yada]
            [schema.core :as schema]
            [clojure.test.check.generators]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.lytek.spec]))

(defn optional [a] (schema/optional-key a))
(defn required [a] a)

(def query-params
  {:character-type {:opt? optional :pristmatic-type String :param-type :query-filter}
   :name           {:opt? optional :pristmatic-type String :param-type :query-filter}
   :id             {:opt? optional :pristmatic-type String :param-type :query-filter}
   :category       {:opt? optional :pristmatic-type String :param-type :query-filter}
   :amount         {:opt? optional :pristmatic-type Number :param-type :query-modifier}
   :owner          {:opt? optional :pristmatic-type String :param-type :query-filter}})

(defn params-to-prismatic [qp's]
  (into {}
        (map (fn [[query-key {:keys [opt? pristmatic-type param-type]}]]
               [(opt? query-key) pristmatic-type])
             qp's)))

(defn params-by-param-type [qp's]
  (reduce (fn [ds [query-key {:keys [opt? pristmatic-type param-type]}]]
            (assoc ds param-type (conj (or (get ds param-type) []) query-key)))
          {}
          qp's))

(defonce *db (atom {}))

(defn filter-data [params]
  (let [{:keys [query-filter]} (params-by-param-type query-params)
        target-map (select-keys params query-filter)
        subfilter-keys (keys target-map)]
    (filter (fn [a]
              (= target-map (select-keys a subfilter-keys)))
            (vals @*db))))

(defn reset-db []
  (reset! *db (->> (gen/sample (s/gen :lytek/solar))
                   (map (fn [a] {(:id a) a}))
                   (into {}))))


(defn api-read [request]
  (let [{:keys [character-type id owner category] :as params}
        (:query (:parameters request))]
    (println "request is " request)
    {:resp   0
     :params params
     :data   (filter-data params)
     ;(str (:query (:parameters request)))
     :error  ""}))

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