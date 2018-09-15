(ns com.blakwurm.yushan.core
    (:require 
            [org.httpkit.server :as httpkit-server :only [run-server]]
            [ring.middleware.keyword-params :as middleware.keyword-params]
            [ring.middleware.params :as middleware.params]
            [ring.middleware.content-type :as middleware.content-type]
            [ring.middleware.not-modified :as middleware.not-modified]
            [ring.middleware.file :as middleware.file]
            [ring.middleware.resource :as middleware.resource]
            [clojure.test.check.generators]
            [ring.util.response :as res]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [honeysql.helpers :as hsql.help]
            [spec-coerce.core :as sc]
            [clojure.tools.namespace.repl :as namespace.repl]
            [com.blakwurm.yushan.routes :as routes]))
            


(defn wrap-bring-params-up [handle-fn]
  (fn [a] (handle-fn (assoc a :params [:foo :bar]))))

(defn wrap-realize-buffer [wrappas]
  (fn [a]
    (wrappas (into a {:body (slurp (:body a))}))))

(defn all-are-welcome [handle-fn]
  (fn [a]
    (assoc-in (handle-fn a)
              [:headers "Access-Control-Allow-Origin"] "*")))

(defn make-middleware []
 (-> #'routes/route-handler                
     middleware.keyword-params/wrap-keyword-params
     middleware.params/wrap-params
     all-are-welcome))
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
