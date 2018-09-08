(ns com.blakwurm.yushan.core
    (:require [yada.yada :as yada]
            [yada.resources.file-resource :as yada.file]
            [bidi.bidi :as bidi]
            [org.httpkit.server :as httpkit-server :only [run-server]]
            [bidi.ring]
            [ring.middleware.keyword-params :as middleware.keyword-params]
            [ring.middleware.params :as middleware.params]
            [ring.middleware.content-type :as middleware.content-type]
            [ring.middleware.not-modified :as middleware.not-modified]
            [ring.middleware.file :as middleware.file]
            [ring.middleware.resource :as middleware.resource]
            [schema.core :as schema]
            [clojure.test.check.generators]
            [ring.util.response :as res]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.blakwurm.lytek.spec :as lyspec]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [honeysql.core :as hsql]
            [honeysql.helpers :as hsql.help]
            [spec-coerce.core :as sc]
            [liberator.core :as liberator :refer [resource defresource]]
            [clojure.tools.namespace.repl :as namespace.repl]
            [com.blakwurm.yushan.api-object :as yushan.api-object]
            [com.blakwurm.yushan.api.entities]))

(defn give-a-thing [request]
  (pr-str (yushan.api-object/find-params :sample request)))

;; In order to dev quickly, we abuse clojure's var system.
;; The following weirdness is written this way so that
;; we don't have to restart the server after every change. 
(defn simple-handler [a]
  (res/response {:stringified-request (give-a-thing a)}))

(defresource api-object
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :handle-ok (yushan.api-object/handle-ok :entities)
  :post! (yushan.api-object/handle-post! :entities)
  :handle-created (yushan.api-object/handle-created :entities))

(defn index-handler [a]
  (assoc-in
    (res/file-response "public/index.html")
    [:headers "Content-Type"]
    "text/html"))

(def routes
  ["/" {"thing" #'simple-handler
        "api" {"/entities" {"/v1" #'api-object}}}])

(def route-handler
  (bidi.ring/make-handler routes))

(defn wrap-bring-params-up [handle-fn]
  (fn [a] (handle-fn (assoc a :params [:foo :bar]))))

(defn wrap-realize-buffer [wrappas]
  (fn [a]
    (wrappas (into a {:body (slurp (:body a))}))))

(defn make-middleware []
 (-> #'route-handler                
     middleware.keyword-params/wrap-keyword-params
     middleware.params/wrap-params))
    ;wrap-realize-buffer))
    ;(middleware.file/wrap-file "public")))
    ;middleware.content-type/wrap-content-type
    ;middleware.not-modified/wrap-not-modified))
     

(def middlewares (make-middleware))

(def *server (atom nil))

(defn start-server []
  ;; stop it if started, for run -main multi-times in repl
  (when-not (nil? @*server) (@*server))
  ;; if no open database, is noop
  ;; (db/close-database!)
  ;; open application global database
  ;; (db/use-database! "jdbc:mysql://localhost/test" "user" "password")

  ;; other init staff, like init-db, init-redis, ...
  (reset! *server (httpkit-server/run-server #'middlewares {:port 3000})))

(defn stop-server []
  (when-not (nil? @*server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@*server :timeout 100)
    (reset! *server nil)))

(defn restart-server []
  (stop-server)
  (start-server))
