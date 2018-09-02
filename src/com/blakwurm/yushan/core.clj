(ns com.blakwurm.yushan.core
    (:require [yada.yada :as yada]
            [yada.resources.file-resource :as yada.file]
            [bidi.bidi :as bidi]
            [org.httpkit.server :as httpkit-server :only [run-server]]
            [bidi.ring]
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
            [clojure.tools.namespace.repl :as namespace.repl]))

(defn simple-handler [a]
    (res/response "Respondo-man"))

(def routes
  ["/" #'simple-handler])

(def *server (atom nil))

(defn start-server []
  ;; stop it if started, for run -main multi-times in repl
  (when-not (nil? @*server) (@*server))
  ;; if no open database, is noop
  ;; (db/close-database!)
  ;; open application global database
  ;; (db/use-database! "jdbc:mysql://localhost/test" "user" "password")

  ;; other init staff, like init-db, init-redis, ...
  (reset! *server (httpkit-server/run-server (bidi.ring/make-handler #'routes) {:port 3000})))

(defn stop-server []
  (when-not (nil? @*server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@*server :timeout 100)
    (reset! *server nil)))

(defn restart-server []
  (stop-server)
  (Thread/sleep 1000)
  (start-server))

