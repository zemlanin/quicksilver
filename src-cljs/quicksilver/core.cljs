(ns ^:figwheel-always quicksilver.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [rum.core :as rum]
            [quicksilver.views.auth]
            [quicksilver.bus :refer [get-sub-chan]]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(enable-console-print!)

(def state (atom {}))

(defn init-login-form-data [node]
  (go-loop []
    (let [[_ msg] (async/<! (get-sub-chan :login-submit))]
      (println msg))
    (recur))
  (swap! state assoc :login (read-string (dommy/attr node "data-init"))))

(rum/defc login-form-component < rum/reactive []
  (let [props (:login @state)]
    (quicksilver.views.auth.login-form props)))

(when-let [login-form (sel1 :#login-form)]
  (init-login-form-data login-form)
  (rum/mount
    (login-form-component)
    login-form))
