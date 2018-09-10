(ns com.blakwurm.yushan.api.relationships
  (:require [clojure.spec.alpha :as s]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.yushan.api-object :as yushan.api-object]
            [com.blakwurm.yushan.db :as  yushan.db]))

(defmethod yushan.api-object/api-object-for :relationships [_]
  (merge
   (yushan.api-object/make-standard-api-object-for :relationships)
   {:columns {:id [:string :primary :key]
              :owner [:string]
              :property [:string]
              :rest [:string]}
    :validation-spec :lytek/relationship
    :delete-safe-key :owner}))

