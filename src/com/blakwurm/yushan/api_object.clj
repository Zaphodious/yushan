(ns com.blakwurm.yushan.api-object
    (:requre [liberator.core :as liberator :refer [resource defresource]]))

(def sample-api-def
  {:name :examplar
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :dessicate (fn [a] a)
   :hydrate (fn [a] a)}) 

(def sample-api-def-doc
  {:name "A keyword denoting the resource's name."
   :column "A map of keyword column names to their sql column spec. Passed into yushan.db/make-table as :column-info."
   :dessicate "Lambda of thing to stored-thing. Transforms the thing for storage in the database."
   :hydrate "Lambda of stored-thing to thing. Transforms the thing from what's stored in the database to a usable thing."})

(defmulti api-object-for identity)

(def template-api-shell
  {:resp 0
   :error ""
   :data []})

(def resource-defaults
  {:available-media-types ["application/json"]})

(defn liberator-resource-for [name-key]
   (let [api-def-of (api-def name-key)]
      {:available-media-types ["application/json"]}))
       

(defn make-api-object [{:keys [] :as param-map}]
  (resource
    :available-media-types ["application/json"]
    :handle-ok {:thing "badboi"}))



