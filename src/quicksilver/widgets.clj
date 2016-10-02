(ns quicksilver.widgets
  (:gen-class)
  (:require [quicksilver.entities :as entities :refer [messages widgets]]
            [clj-time.core :as t]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [clojure.spec :as s]))

(defn repeat-hash-map->seq [repeat-hash-map]
  "(repeat-hash-map->seq {:a 5 :b 7}) => (:b :b :b :b :b :b :b :a :a :a :a :a)"
  (flatten (map (fn [[k v]] (repeat v k)) repeat-hash-map)))

(defn chance-maps->seq [chance-maps]
  "(chance-maps->seq [{:text :a :chance 5} {:text :b :chance 7}]) => (:b :b :b :b :b :b :b :a :a :a :a :a)"
  (flatten
    (map
      (fn [{t :text c :chance}] (repeat c t))
      chance-maps)))

(defn random-text [widget]
  (let [widget-id (:id widget)
        widget-message (entities/get-widget-message widget-id)
        source-data (:source_data widget)]
      (if (and widget-message (t/after? (:date_created widget-message) (t/today-at-midnight)))
        (select-keys widget-message [:text])
        (-> (:values source-data)
            (chance-maps->seq)
            (rand-nth)
            (entities/insert-message {:widget_id widget-id})
            (select-keys [:text])))))

(defn static-text [widget]
  (-> (:id widget)
      (entities/get-widget-message)
      (select-keys [:text])))

(defn get-base-timestamp [date-created value-index period-length]
  "
  returns moment in which, we assume, period have started
  `value-index` is used to shift start in a case if user have set a specific value by hand

  TODO: edge case of (* expires period-length) > epoch
  "
  (t/minus (or date-created (t/epoch)) (t/seconds (* value-index period-length))))

(defn periodic-text [widget]
  (let [widget-message (entities/get-widget-message (:id widget))
        source-data (:source_data widget)
        periodic-values (:values source-data)
        period-length (:switches-every source-data)
        value-index (max 0 (.indexOf periodic-values (:text widget-message)))
        base-timestamp (get-base-timestamp (:date_created widget-message) value-index period-length)
        time-delta (t/in-seconds (t/interval base-timestamp (t/now)))]
    (-> periodic-values
        (nth (quot (mod time-delta (* (count periodic-values) period-length)) period-length))
        (#(hash-map :text %)))))

(s/def :random-text.value/text string?)
(s/def :random-text.value/chance integer?)
(s/def :random-text/value (s/keys :req-un [:random-text.value/text
                                            :random-text.value/chance]))
(s/def :random-text/values (s/every :random-text/value :min-count 2))
(s/def ::random-text (s/keys :req-un [:random-text/values]))

(s/def :periodic-text/values (s/every string? :min-count 2))
(s/def :periodic-text/switches-every integer?)
(s/def ::periodic-text (s/keys :req-un [:periodic-text/values
                                        :periodic-text/switches-every]))

(defn match-widget-type [widget]
  (match widget
    {:type "random-text"} (random-text widget)
    {:type "static-text"} (static-text widget)
    {:type "periodic-text"} (periodic-text widget)
    nil {:error "not found"}
    :else {:error "unknown widget type"}))
