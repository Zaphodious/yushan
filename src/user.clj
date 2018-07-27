(ns user
  (:require [clojure.tools.nrepl.server :as nrepl]))

(println "user ns loaded")

(defn foo []
  (println "booyeah!"))

(defn start-nrepl []
  (nrepl/start-server :port 2539))

(defn -main []
  (start-nrepl))
