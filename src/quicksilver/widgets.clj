(ns quicksilver.widgets
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :refer [messages widgets]]
            [clj-time.core :as t]
            [clojure.data.json :as json]))

(defn get-widget [widget-id]
  (-> (select widgets
        (where {:id widget-id}))
      (first)))

(defn get-widget-message [widget]
  (-> (select messages
        (where {:widget_id (:id widget)})
        (limit 1)
        (order :date_created :DESC))
      (first)))

(defn insert-widget-message [text widget]
  (-> (insert messages
        (values {:type "auto", :text text, :widget-id (:id widget)}))))

(defn repeat-hash-map->seq [repeat-hash-map]
  "(repeat-hash-map->seq {:a 5 :b 7}) => (:b :b :b :b :b :b :b :a :a :a :a :a)"
  (flatten (map (fn [[k v]] (repeat v k)) repeat-hash-map)))

(defn random-text [widget]
  (let [widget-message (get-widget-message widget)
        source-data (:source-data widget)]
      (if (and widget-message (t/after? (:date-created widget-message) (t/today-at-midnight)))
        (select-keys widget-message [:text])
        (-> (:source-data widget)
            (json/read-str)
            (get "values")
            (repeat-hash-map->seq)
            (rand-nth)
            (insert-widget-message widget)
            (select-keys [:text])))))
