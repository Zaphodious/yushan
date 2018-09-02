(ns drinkcider
    (:require [nrepl.server :as nrepl-server]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn -main []
  (nrepl-server/start-server :port 4242 :handler (nrepl-handler)))