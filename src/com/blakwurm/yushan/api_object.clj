(ns com.blakwurm.yushan.api-object
    (:require [liberator.core :as liberator] 
              [clojure.spec.alpha :as s]
              [spec-coerce.core :as sc]
              [com.blakwurm.lytek.spec :as lyspec]
              [clojure.set :as c.set]
              [clojure.edn :as edn]
              [clojure.data.json :as json]
              [com.blakwurm.yushan.db :as yushan.db]
              [clojure.string :as str])
    (:import java.util.Base64))

(defmulti api-object-for identity)

(defn find-params [api-object-name context]
  (let [{:keys [prepare-params] :as api-object} (api-object-for api-object-name)]
    (prepare-params (:params (:request context)))))

(defn split-map 
  "Accepts a map and a seq of keys. Returns a vector containing [a map with just the keys from the, a map with the rest of the keys."
  [m split-keys]
  (let [inclusive-m (select-keys m split-keys)
        exclusive-m (select-keys m (c.set/difference (set (keys m))
                                                     (set split-keys)))]
    [inclusive-m exclusive-m]))
(defn determine-api-response-code [seq-of-things]
  (let [db-ops-didnt-succeed (false? (first (filter false? seq-of-things)))]
    (cond
      db-ops-didnt-succeed 1
      :default [0 ""])))

(defn make-api-response [api-name seq-of-things]
  (if (boolean? seq-of-things)
     {:resp 1
      :data [nil]
      :error ""}
   (let [{:keys [hydrate] :as api-map} (api-object-for api-name)
         [resp-code error-message] (determine-api-response-code seq-of-things)]
     {:resp resp-code
      :data (map hydrate seq-of-things)
      :error error-message})))

(defn standard-dessicate [api-name thing]
  (let [{:keys [columns] :as api-map} (api-object-for api-name)
        column-names (map first columns)
        [slim-map rest-map] (split-map thing column-names)
        store-map (assoc slim-map :rest (pr-str rest-map))]
    store-map)) 
                      
(defn standard-hydrate [thing]
  (let [the-rest (:rest thing)
        thing-without-rest (dissoc thing :rest)
        hydrated-rest (edn/read-string the-rest)]
    (merge thing-without-rest hydrated-rest))) 

(defn encode-64 [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn standard-gen-id [api-name]
    (let [randostring (-> (str (rand))
                          (str (rand))
                          (str (rand))
                          (str (rand))
                          (str (rand))
                          (str (rand))
                          encode-64
                          encode-64
                          (subs 10 27))
           api-pref (reduce str (take 3 (name api-name)))            
           subrando (str "YU" api-pref randostring)]
      subrando))
        

(defmethod api-object-for :sample [_]
  {:name :sample
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :dessicate #(standard-dessicate :sample %)
   :hydrate standard-hydrate 
   :prepare-params lyspec/coerce-structure
   :generate-new-id #(standard-gen-id :sample)})

(def sample-api-def-doc
  {:name "A keyword denoting the resource's name."
   :columns "A map of keyword column names to their sql column spec. Passed into yushan.db/make-table as :column-info."
   :dessicate "Lambda of thing to stored-thing. Transforms the thing for storage in the database."
   :hydrate "Lambda of stored-thing to thing. Transforms the thing from what's stored in the database to a usable thing."
   :prepare-params "Lambda of 'raw' params from ring request, to params used by inner logic. Default is identity."
   :generate-new-id "0-arity non-deterministic lambda used for generating a new ID for an API record."})

(def empty-api-response
  {:resp 0
   :error ""
   :data []})


(def resource-defaults
  {:available-media-types ["application/json"]})

(defn liberator-resource-for [name-key]
   (let [api-def-of (api-object-for name-key)]
      (liberator/resource resource-defaults :handle-ok identity))) 

(defn make-api-object [{:keys [] :as param-map}]
  (liberator/resource
    :available-media-types ["application/json"]
    :handle-ok {:thing "badboi"}))

(defn realize-write-data [request]
  (let [the-body (json/read-str (slurp (:body request))
                                :key-fn keyword)]
    (or (:data the-body) the-body)))

(defn handle-post! [api-name]
  (fn [{:keys [request] :as fn-param}]
    (let [write-data (realize-write-data request)
          {:keys [dessicate generate-new-id]} (api-object-for api-name)
          new-ids (take (count write-data) (repeatedly generate-new-id))
          newly-id-writes (map (fn [new-id ent] (assoc ent :id new-id)) new-ids write-data)
          raw-write-result (yushan.db/insert-many {:table api-name
                                                   :transform-fn dessicate
                                                   :thing newly-id-writes})
          id-write-result (map
                            (fn [the-id write-res]
                              (if write-res the-id false))
                            new-ids
                            raw-write-result)]
                        
     (println write-data)
     {::insert-result id-write-result})))

(defn handle-ok [api-name]
  (fn [{:as fn-param :keys [request]}]
    (let [{:keys [prepare-params hydrate columns]} (api-object-for api-name)  
          conformed-params (prepare-params (:params request))
          [actual-p secondary-p] (split-map conformed-params (keys columns))
          query-params (into secondary-p
                             {:table api-name
                              :query actual-p
                              :transform-fn hydrate})
          query-result (yushan.db/read-many query-params)
          api-responso (make-api-response api-name query-result)]
      query-result)))   

(defn handle-created [api-name]
  (fn [{:as fn-param :keys [request]}]
    {:body (::insert-result fn-param)}))

(defn make-table-for-api-name [api-name]
  (let [{:keys [columns]} (api-object-for api-name)]
   (yushan.db/make-table {:table api-name
                          :column-info columns})))
