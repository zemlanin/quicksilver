(ns quicksilver.redis
  (:gen-class)
  (:require [taoensso.carmine :as car]
            [quicksilver.config :refer [config]]))

(def server1-conn {:pool nil :spec (config :redis)})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(def ping car/ping)
