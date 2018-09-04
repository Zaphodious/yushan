(ns com.blakwurm.yushan.api.entities
  (:require [clojure.spec.alpha :as s]
            [com.blakwurm.lytek.spec :as lyspec]
            [com.blakwurm.yushan.api-object :as yushan.api-object]))

(defmethod yushan.api-object/api-object-for :entities [_]
  {:name :entities
   :columns {:name [:string]
             :id [:string :primary :key]
             :description [:string]
             :category [:string]
             :subcategory [:string]
             :variation [:string]}
   :prepare-params lyspec/coerce-structure
   :dessicate #(yushan.api-object/standard-dessicate :entities %) 
   :hydrate yushan.api-object/standard-hydrate})
