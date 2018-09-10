(ns com.blakwurm.yushan.api.entities
  (:require [clojure.spec.alpha :as s]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.yushan.api-object :as yushan.api-object]
            [com.blakwurm.yushan.db :as  yushan.db]))

(defmethod yushan.api-object/api-object-for :entities [_]
  (merge
   (yushan.api-object/make-standard-api-object-for :entities)
   {:columns {:name [:string]
              :id [:string :primary :key]
              :description [:string]
              :category [:string]
              :subcategory [:string]
              :variation [:string]
              :rest [:string]}
    :validation-spec :lytek/entity
    :delete-safe-key :name}))

