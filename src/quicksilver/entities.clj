(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :as k :refer [defentity prepare transform entity-fields table belongs-to has-many]]))

(defentity messages
  (entity-fields :id :date_created :author :type :text :widget_id))

(defentity slack-tokens
  (table :slack_tokens)
  (entity-fields :id :date_created :token))

(defn pg-object->str [v & ks]
  (reduce #(assoc %1 %2 (if-some [pg-obj (%2 %1)] (.getValue pg-obj))) v ks))

(defentity widgets
  (transform (fn [v] (-> v
                        (pg-object->str :type :source_data))))
  (entity-fields :id :type :date_created :source_data :title))

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

(defn get-widget-message [widget]
  (-> (k/select messages
        (k/where {:widget_id (:id widget)})
        (k/limit 1)
        (k/order :date_created :DESC))
      (first)))
