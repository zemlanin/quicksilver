(ns ^:figwheel-always quicksilver.core
  (:require [rum.core :as rum]
            [quicksilver.app :as app :refer [app-component]]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(enable-console-print!)

(rum/mount
  (app-component)
  (sel1 :#app))

(defonce setup-initiate-state
  (do
    (app/initiate-state)))

(defonce setup-listeners
  (do
    (app/setup-listeners)))
