(ns user
  (:require [clojure.tools.nrepl.server :as nrepl]
            [com.blakwurm.yushan.core :as yushan]
            [clojure.tools.namespace.repl :as namespace.repl]))

(defn plugin
  "Load and switch to the 'dev' namespace."
  []
  (yushan/start-server)
  (require 'com.blakwurm.yushan.core)
  (in-ns 'com.blakwurm.yushan.core)
  :loaded)