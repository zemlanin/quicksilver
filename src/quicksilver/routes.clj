(ns quicksilver.routes
  (:gen-class)
  (:require [quicksilver.config :refer [config]]
            [clojure.core.match :refer [match]]))

(defn debug [] (:debug (config)))
(defn port [] (:port (config)))
(defn base-url [] (:base-url (config)))

(defn absolute
  ([url] (str (match  [(debug) (.startsWith url "/ws/")]
                      [true false]   "http://"
                      [false false]  "https://"
                      [true true]    "ws://"
                      [false true]   "wss://")
              (base-url)
              (when (debug) (str ":" (port)))
              url))
  ([url & ps] (let [params (apply hash-map ps)
                    paramless-url (absolute url)]
                  (reduce (fn [u [k v]] (clojure.string/replace u (str k) (str v))) paramless-url params))))
