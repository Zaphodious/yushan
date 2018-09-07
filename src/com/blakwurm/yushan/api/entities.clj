(ns com.blakwurm.yushan.api.entities
  (:require [clojure.spec.alpha :as s]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.yushan.api-object :as yushan.api-object]
            [com.blakwurm.yushan.db :as  yushan.db]))

(defn make-random-entity []
  (gen/generate (s/gen :lytek/entity)))
  

(defmethod yushan.api-object/api-object-for :entities [_]
  {:name :entities
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :category [:string]
             :subcategory [:string]
             :variation [:string]
             :rest [:string]}
   :prepare-params lyspec/coerce-structure
   :dessicate #(yushan.api-object/standard-dessicate :entities %) 
   :hydrate yushan.api-object/standard-hydrate})

(defn add-n-test-entities [n]
  (let [{:keys [dessicate hydrate]} (yushan.api-object/api-object-for :entities)]
    (yushan.api-object/make-table-for-api-name :entities)
    (map (fn [a]
          (yushan.db/insert-one {:table :entities
                                 :transform-fn dessicate
                                 :thing a}))
         (repeatedly n make-random-entity))))
  
  
