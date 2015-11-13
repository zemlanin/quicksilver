(ns quicksilver.redis
  (:gen-class)
  (:refer-clojure :exclude [get set key])
  (:require [taoensso.carmine :as car]
            [quicksilver.config :refer [config]]))

(def server1-conn {:pool nil :spec (config :redis)})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(def ping car/ping)
(def key car/key)
(def get car/get)
(def set car/set)
(def setex car/setex)
(def del car/del)
