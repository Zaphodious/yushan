(ns com.blakwurm.yushan.routes
    (:require [com.blakwurm.yushan.api-object :as yushan.api-object]
              [liberator.core :as liberator :refer [resource defresource]]
              [bidi.bidi :as bidi]
              [bidi.ring]
              [ring.util.response :as res]
              [com.blakwurm.yushan.api.entities]
              [com.blakwurm.yushan.api.relationships]))


(defn give-a-thing [request]
  (pr-str (yushan.api-object/find-params :sample request)))

;; In order to dev quickly, we abuse clojure's var system.
;; The following weirdness is written this way so that
;; we don't have to restart the server after every change. 
(defn simple-handler [a]
  (res/response {:stringified-request (give-a-thing a)}))

(defresource entity-api-object
  :available-media-types ["application/json"]
  :allowed-methods [:get :post :put :delete]
  :handle-ok (yushan.api-object/handle-ok :entities)
  :post! (yushan.api-object/handle-post! :entities)
  :put! (yushan.api-object/handle-put! :entities)
  :delete! (yushan.api-object/handle-delete! :entities)
  :handle-created (yushan.api-object/handle-created :entities)
  :handle-no-content (yushan.api-object/handle-no-content :entities)
  :new? (yushan.api-object/determine-new :entities)
  :respond-with-entity? (fn [a] true))

(defresource relationship-api-object
  :available-media-types ["application/json"]
  :allowed-methods [:get :post :put :delete]
  :handle-ok (yushan.api-object/handle-ok :relationships)
  :post! (yushan.api-object/handle-post! :relationships)
  :put! (yushan.api-object/handle-put! :relationships)
  :delete! (yushan.api-object/handle-delete! :relationships)
  :handle-created (yushan.api-object/handle-created :relationships)
  :handle-no-content (yushan.api-object/handle-no-content :relationships)
  :new? (yushan.api-object/determine-new :relationships)
  :respond-with-entity? (fn [a] true))

(defn index-handler [a]
  (assoc-in
    (res/file-response "public/index.html")
    [:headers "Content-Type"]
    "text/html"))

(def routes
  ["/" {"thing" #'simple-handler
        "entities" {"/publius" {"/v1" #'entity-api-object}}
        "relationships" {"/publius" {"/v1" #'relationship-api-object}}}])

(def route-handler
  (bidi.ring/make-handler routes))
