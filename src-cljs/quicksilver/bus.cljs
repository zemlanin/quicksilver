(ns quicksilver.bus
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async]))

(def event-bus (async/chan))
(def event-bus-pub (async/pub event-bus first))

(defn preventless-put [type]
  (fn [e]
    (async/put! event-bus [type e])
    nil))

(defn prevent-and-put [type]
  (fn [e]
    (.preventDefault e)
    (async/put! event-bus [type e])
    nil))

(defn get-sub-chan [type]
  (let [ch (async/chan)]
    (async/sub event-bus-pub type ch)
    ch))
