(ns quicksilver.websockets
  (:gen-class)
  (:require [clojure.core.async :as async :refer (<! <!! >! go chan go-loop close! sub pub)]
            [org.httpkit.server :refer [with-channel on-close send! on-receive]]))

(def pings (chan))
(def sub-pings (pub pings :subj))

(defn process
  [_]
  (let [ch (chan)]
    (sub sub-pings :pong ch)
    ch))

(defn ws-handler [request]
  (let [c (process request)]
    (with-channel request channel
      (on-close channel (fn [status] (close! c)))
      (go-loop []
        (when-let [v (<! c)]
          (send! channel {:status 200 :body (str v)})
          (recur)))
      (on-receive channel (fn [data] (send! channel data))))))
