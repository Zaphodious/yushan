(ns com.blakwurm.yushan.api-object
    (:require [liberator.core :as liberator] 
              [liberator.representation :as librep]
              [clojure.spec.alpha :as s]
              [spec-coerce.core :as sc]
              [ring.util.response :as res]
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
  (let [db-ops-didnt-succeed (not (= (count seq-of-things)
                                     (count (filter identity seq-of-things))))
        set-of-things (set seq-of-things)
        error-exists? (fn [error-key] (contains? set-of-things error-key))]
    (cond
      (error-exists? ::write-failed) [3 "Some things not written"]
      (error-exists? ::spec-failed) [2 "Some things not valid"]
      (error-exists? ::delete-failed) [4 "No Delete Happened"]
      (error-exists? nil) [1 "Some Things Not Found"]
      (error-exists? false) [1 "Some Things Not Found"]
      :default [0 ""])))

(defn make-api-response [api-name seq-of-things]
  (if (boolean? seq-of-things)
      {:resp 1
       :data [nil]
       :error ""}
   (let [{:keys [hydrate] :as api-map} (api-object-for api-name)
         [resp-code error-message] (determine-api-response-code seq-of-things)]
      {:resp resp-code
       :data (map (fn [a] 
                    (if (map? a)
                     (hydrate a) 
                     a))
                  seq-of-things)
       :error error-message})))

(defn stringify-keyword-values [mappo]
  (when (map? mappo)
    (into {}
      (map (fn [[k v]]
             [k (if (keyword? v)
                    (name v)
                    v)])
           mappo))))

(defn standard-dessicate [api-name thing]
  (let [{:keys [columns] :as api-map} (api-object-for api-name)
        column-names (map first columns)
        [slim-map rest-map] (split-map thing column-names)
        stringered-slim-map (stringify-keyword-values slim-map)
        store-map (assoc stringered-slim-map :rest (pr-str rest-map))]
    (println stringered-slim-map)
    store-map)) 
                      
(defn standard-hydrate [api-name thing]
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
        

(defn standard-validation-determine [api-name thing]
  (let [{:keys [validation-spec validation-spec validation-coersion]} (api-object-for api-name)]
     (when thing (->> thing validation-coersion (s/valid? validation-spec)))))

(defn standard-prepare-params [api-name params]
  (->> params lyspec/coerce-structure stringify-keyword-values))

(defmethod api-object-for :sample [_]
  {:name :sample
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :dessicate #(standard-dessicate :sample %)
   :hydrate #(standard-hydrate :sample %) 
   :prepare-params #(standard-prepare-params :sample %)
   :generate-new-id #(standard-gen-id :sample)
   :validation-spec any?
   :validation-coersion lyspec/coerce-structure
   :validation-determine #(standard-validation-determine :sample %)         
   :delete-safe-key :name})

(def sample-api-def-doc
  {:name "A keyword denoting the resource's name."
   :columns "A map of keyword column names to their sql column spec. Passed into yushan.db/make-table as :column-info."
   :dessicate "Lambda of thing to stored-thing. Transforms the thing for storage in the database."
   :hydrate "Lambda of stored-thing to thing. Transforms the thing from what's stored in the database to a usable thing."
   :prepare-params "Lambda of 'raw' params from ring request, to params used by inner logic. Default is identity."
   :generate-new-id "0-arity non-deterministic lambda used for generating a new ID for an API record."
   :validation-spec "A clojure.spec that will be used to validate data before it touches the database"
   :validation-coersion "A lambda that coerses the types in a structure to be appropriate for validation. Necessary, as conversion from edn to json is inherently lossy."
   :validation-determine "A lambda that determines if a given thing is a valid 'thing'"
   :delete-safe-key "A field key to be checked before deletion. Both ID and this field must match before a deletion happens."})

(defn make-standard-api-object-for [new-api-name]
  {:name new-api-name
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :prepare-params #(standard-prepare-params new-api-name %)
   :dessicate #(standard-dessicate new-api-name %) 
   :hydrate #(standard-hydrate new-api-name %)
   :generate-new-id #(standard-gen-id new-api-name)
   :validation-spec any?
   :validation-coersion lyspec/coerce-structure
   :validation-determine #(standard-validation-determine new-api-name %)
   :delete-safe-key :name})

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
          {:keys [dessicate generate-new-id validation-determine]} (api-object-for api-name)
          new-ids (take (count write-data) (repeatedly generate-new-id))
          validated-writes (map validation-determine write-data)
          newly-id-writes (map (fn [new-id ent] (if (map? ent) (assoc ent :id new-id) ent) new-ids write-data))
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

(defn handle-put! [api-name]
  (fn [{:keys [request] :as fn-param}]
     (let [update-data (realize-write-data request)
           {:keys [prepare-params hydrate columns validation-coersion validation-spec validation-determine dessicate]} (api-object-for api-name)  
           existant-data (map (fn [{:keys [id] :as thing}]
                                 (yushan.db/read-one {:table api-name
                                                      :transform-fn hydrate
                                                      :query {:id id}}))
                              update-data)
           merged-data (map (fn [map-existant map-update]
                               (when map-existant
                                 (merge map-existant map-update)))
                            existant-data update-data)
           valid-data  (map (fn [a]
                              (if (validation-determine a)
                                a
                                ::spec-failed))
                            merged-data)
           validated-data (map validation-determine 
                               merged-data)
           update-results (map (fn [thing]
                                 (if (and thing (not (= thing ::spec-failed)))
                                    (if (yushan.db/update-one {:table api-name
                                                               :transform-fn dessicate
                                                               :thing thing})
                                       (:id thing))
                                   thing))
                            valid-data)
           debug-explain (map (fn [a] (->> a validation-coersion (s/explain-str validation-spec)))
                           merged-data)]
       {::updated-ids update-results})))

(defn handle-delete!
  "Returns a function that ONLY deletes a record if both the ID and the delete-safe-key match the existant record"
  [api-name]
  (fn [{:keys [request] :as fn-param}]
     (let [{:keys [prepare-params hydrate delete-safe-key]} (api-object-for api-name)
           params (prepare-params (:params request ()))
           entity-to-be-deleted (yushan.db/read-one
                                  {:table api-name
                                   :query {delete-safe-key (or (delete-safe-key params) "")
                                           :id   (or (:id params) "")}
                                   :transform-fn hydrate})
           delete-result (when entity-to-be-deleted
                           (yushan.db/delete-one {:table api-name
                                                  :id (:id params)}))]
        {::delete-result (if delete-result
                           (:id params)
                           ::delete-failed)})))
                           

(defn handle-ok-updated [api-name {:keys [request] :as fn-param}]
    (make-api-response api-name (::updated-ids fn-param)))

(defn handle-ok-deleted [api-name {:keys [request] :as fn-param}]
  (make-api-response api-name [(::delete-result fn-param)]))

(defn handle-ok-query [api-name {:as fn-param :keys [request updated-ids]}]
    (let [
          {:keys [prepare-params hydrate columns]} (api-object-for api-name)  
          conformed-params (prepare-params (:params request))
          [actual-p secondary-p] (split-map conformed-params (keys columns))
          query-params (into secondary-p
                             {:table api-name
                              :query actual-p
                              :transform-fn hydrate})
          query-result (yushan.db/read-many query-params)
          api-responso (make-api-response api-name query-result)]
      (make-api-response api-name query-result)))   

(defn handle-ok [api-name]
  (fn [{:as fn-param :keys [request]}]
    (let [handle-fn (cond
                      (::updated-ids fn-param) handle-ok-updated
                      (::delete-result fn-param) handle-ok-deleted
                      :default handle-ok-query)]
      (handle-fn api-name fn-param))))
     

(defn handle-created [api-name]
  (fn [{:as fn-param :keys [request]}]
    (make-api-response api-name (::insert-result fn-param))))

(defn determine-new [api-name]
  (fn [{:as fn-param :keys [request]}]
    (::insert-result fn-param)))

(defn handle-no-content [api-name]
  (fn [a]
    empty-api-response))

(defn make-table-for-api-name [api-name]
  (let [{:keys [columns]} (api-object-for api-name)]
   (yushan.db/make-table {:table api-name
                          :column-info columns})))
