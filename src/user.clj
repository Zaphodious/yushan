(ns user
  (:require [clojure.tools.nrepl.server :as nrepl]
            [com.blakwurm.yushan.core :as yushan]
            [dev]
            [clojure.tools.namespace.repl :as namespace.repl]))


(defn go
  "Load and switch to the 'dev' namespace."
  [& opts]
  (dev/start-nrepl)
  (yushan/start-server)
  (require 'com.blakwurm.yushan.core)
  (in-ns 'com.blakwurm.yushan.core)
  :loaded)

(defn plugin
  "Load and switch to the 'dev' namespace."
  []
  (yushan/start-server)
  (require 'com.blakwurm.yushan.core)
  (in-ns 'com.blakwurm.yushan.core)
  :loaded)
