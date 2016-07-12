(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :as k]))

(k/defentity messages
  (k/entity-fields :id :date_created :author :text :widget_id))

(defn pg-object->str [v & ks]
  (reduce #(assoc %1 %2 (if-some [pg-obj (%2 %1)] (.getValue pg-obj))) v ks))

(k/defentity widgets
  (k/transform (fn [v] (-> v
                        (pg-object->str :type :source_data))))
  (k/entity-fields :id :type :date_created :source_data :title))

(defn get-widget [widget-id]
  (-> (k/select widgets
        (k/where {:id widget-id})
        (k/limit 1))
      (first)))

(defn get-widget-by-title [title]
  (-> (k/select widgets
        (k/where {:title title})
        (k/limit 1))
      (first)))

(defn get-widget-message [widget-id]
  (-> (k/select messages
        (k/where {:widget_id widget-id})
        (k/limit 1)
        (k/order :date_created :DESC))
      (first)))

(defn insert-message
  ([msg] (k/insert messages (k/values msg)))
  ([text msg] (insert-message (assoc msg :text text))))
