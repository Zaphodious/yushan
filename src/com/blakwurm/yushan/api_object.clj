(ns com.blakwurm.yushan.api-object
    (:require [liberator.core :as liberator] 
              [clojure.spec.alpha :as s]
              [spec-coerce.core :as sc]
              [com.blakwurm.lytek.spec :as lyspec]
              [clojure.set :as c.set]
              [clojure.edn :as edn]))

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
  (let [{:keys [hydrate] :as api-map} (api-object-for api-name)
        [resp-code error-message] (determine-api-response-code seq-of-things)]
   {:resp resp-code
    :data (map hydrate seq-of-things)
    :error error-message}))

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

(defmethod api-object-for :sample [_]
  {:name :sample
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :dessicate (partial standard-dessicate :sample)
   :hydrate standard-hydrate 
   :prepare-params lyspec/coerce-structure})

(def sample-api-def-doc
  {:name "A keyword denoting the resource's name."
   :column "A map of keyword column names to their sql column spec. Passed into yushan.db/make-table as :column-info."
   :dessicate "Lambda of thing to stored-thing. Transforms the thing for storage in the database."
   :hydrate "Lambda of stored-thing to thing. Transforms the thing from what's stored in the database to a usable thing."
   :prepare-params "Lambda of 'raw' params from ring request, to params used by inner logic. Default is identity."})

(def template-api-shell
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



