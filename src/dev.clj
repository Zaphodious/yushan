(ns dev
  (:require [clojure.tools.nrepl.server :as nrepl]
            [com.blakwurm.yushan.core :as yushan]
            [clojure.tools.namespace.repl :as namespace.repl]))

(println "user ns loaded")

(defn foo []
  (println "booyeah!"))

(def *nrepl-server (atom {}))

(defn start-nrepl []
  (reset! *nrepl-server (nrepl/start-server :port 4242)))

(defn stop-nrepl []
  (nrepl/stop-server @*nrepl-server))

(defn start-server []
  (yushan/start-server))

(defn stop-server []
  (yushan/stop-server))

(defn start-all []
  (do
    (start-server)))

(defn reset []
  (do
    (stop-server)
    (namespace.repl/refresh)
    (com.blakwurm.yushan.core/start-server)))

(defn start-nrepl []
  (nrepl/start-server :port 4242)
  (println "Ripple In!"))

(defn -main []
  (start-nrepl))

