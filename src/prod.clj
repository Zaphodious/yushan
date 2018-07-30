(ns prod
  (:require [yada.yada :as yada]
            [clojure.tools.nrepl.server :as nrepl]
            [com.blakwurm.yushan.core :as yushan]))

(defn start-nrepl []
  (nrepl/start-server :port 4242)
  (println "Ripple In!"))

(defn start-server []
  (yushan/start-server))

(defn -main []
  (println "Starting Server")
  (start-server)
  (println "Server Started")
  (println "Starting nRepl")
  (start-nrepl)
  (println "nrepl started"))