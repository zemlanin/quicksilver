(ns quicksilver.websockets
  (:gen-class)
  (:require [clojure.core.async :as async :refer (<! >! go chan go-loop close! sub pub)]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [org.httpkit.server :refer [with-channel on-close send! on-receive]]))

(def url "/ws/:subj")

(def pings (chan))
(def sub-pings (pub pings :subj))

(defn process [{route-params :route-params :as req}]
  (when-let [subj (:subj route-params)]
    (let [ch (chan)]
      (sub sub-pings (keyword subj) ch)
      ch)))

(defn ws-handler [request]
  (let [c (process request)]
    (with-channel request channel
      (on-close channel (fn [status] (close! c)))
      (go-loop []
        (when-let [v (<! c)]
          (send! channel {:status 200 :body (json/write-str v :key-fn ->camelCaseString)})
          (recur)))
      (on-receive channel (fn [data] (send! channel data))))))
