(ns com.blakwurm.yushan.api-object
    (:require [liberator.core :as liberator] 
              [clojure.spec.alpha :as s]
              [spec-coerce.core :as sc]
              [com.blakwurm.lytek.spec :as lyspec]))

(defmulti api-object-for identity)

(defn find-params [api-object-name context]
  (let [{:keys [prepare-params] :as api-object} (api-object-for api-object-name)]
    (prepare-params (:params (:request context)))))

(defmethod api-object-for :sample [_]
  {:name :sample
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :rest [:string]}
   :dessicate (fn [a] a)
   :hydrate (fn [a] a) 
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



