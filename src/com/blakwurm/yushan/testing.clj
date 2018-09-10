(ns com.blakwurm.yushan.testing
  (:require [clojure.spec.alpha :as s]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.yushan.api-object :as yushan.api-object]
            [com.blakwurm.yushan.db :as  yushan.db]))

(defn make-random-thing [api-name]
  (let [{:keys [validation-spec]} (yushan.api-object/api-object-for api-name)]
    (gen/generate (s/gen validation-spec))))
  
(defn add-n-test-things [api-name n]
  (let [{:keys [dessicate hydrate generate-new-id]} (yushan.api-object/api-object-for api-name)]
    (yushan.api-object/make-table-for-api-name api-name)
    (map (fn [a]
          (yushan.db/insert-one {:table api-name
                                 :transform-fn dessicate
                                 :thing (assoc a :id (generate-new-id))}))
         (repeatedly n #(make-random-thing api-name)))))
  
  
