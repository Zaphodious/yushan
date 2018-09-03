(ns com.blakwurm.yushan.api-object
    (:requre [liberator.core :as liberator :refer [resource defresource]]))

(defn make-api-object [{:keys [] :as param-map}]
  (resource
    :available-media-types ["application/json"]
    :handle-ok {:thing "badboi"}))
