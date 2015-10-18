(ns quicksilver.widgets
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :refer [messages widgets]]
            [clj-time.core :as t]
            [clj-time.coerce :as time-coerce]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.data.json :as json]
            [schema.core :as s]
            ))

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

(defn insert-auto-message [text widget]
  (-> (insert messages
        (values {:text text, :widget-id (:id widget)}))))

(defn repeat-hash-map->seq [repeat-hash-map]
  "(repeat-hash-map->seq {:a 5 :b 7}) => (:b :b :b :b :b :b :b :a :a :a :a :a)"
  (flatten (map (fn [[k v]] (repeat v k)) repeat-hash-map)))

(defn random-text [widget]
  (let [widget-message (get-widget-message widget)
        source-data (json/read-str (:source-data widget))]
      (if (and widget-message (t/after? (:date-created widget-message) (t/today-at-midnight)))
        (select-keys widget-message [:text])
        (-> (get source-data "values")
            (repeat-hash-map->seq)
            (rand-nth)
            (insert-auto-message widget)
            (select-keys [:text])))))

(defn static-text [widget]
  (-> (get-widget-message widget)
      (select-keys [:text])))

(defn get-base-timestamp [date-created value-index period-length]
  "
  returns moment in which, we assume, period have started
  `value-index` is used to shift start in a case if user have set a specific value by hand

  TODO: edge case of (* expires period-length) > epoch  
  "
  (t/minus (or date-created (t/epoch)) (t/seconds (* value-index period-length))))

(defn periodic-text [widget]
  (let [widget-message (get-widget-message widget)
        source-data (json/read-str (:source-data widget) :key-fn ->kebab-case-keyword)
        periodic-values (:values source-data)
        period-length (:switches-every source-data)
        value-index (max 0 (.indexOf periodic-values (:text widget-message)))
        base-timestamp (get-base-timestamp (:date-created widget-message) value-index period-length)
        time-delta (t/in-seconds (t/interval base-timestamp (t/now)))]
    (-> periodic-values
        (nth (quot (mod time-delta (* (count periodic-values) period-length)) period-length))
        (#(hash-map :text %)))))

(def data-schemas {
  :random-text {
    :values [{:text s/Str
              :chance s/Int}]}
  :periodic-text {
    :values [s/Str]
    :switches-every s/Int}
  })
