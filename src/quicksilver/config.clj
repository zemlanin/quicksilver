(ns quicksilver.config
  (:gen-class)
  (:require [clojure.java.io :as io]
            [nomad :refer [defconfig]]))

(defconfig config (io/resource "config/config.edn"))
