(ns dev
  (:require [yada.yada :as yada]
            [clojure.tools.nrepl.server :as nrepl]))

(defn start-nrepl []
  (nrepl/start-server :port 4242))

(defn -main []
  (start-nrepl))

