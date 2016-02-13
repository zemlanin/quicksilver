(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :refer [defentity prepare transform entity-fields table belongs-to has-many]]))

(defentity messages
  (entity-fields :id :date_created :author :type :text :widget_id))

(defentity slack-tokens
  (table :slack_tokens)
  (entity-fields :id :date_created :token))

(defn pg-object->str [v & ks]
  (reduce #(assoc %1 %2 (if-some [pg-obj (%2 %1)] (.getValue pg-obj))) v ks))

(def old-widgets-map {"duty" 1
                      "default" 2
                      "quote" 3
                      "uno" 4
                      "gif" 5
                      "ready" 6})

(defentity widgets
  (transform (fn [v] (-> v
                        (pg-object->str :type :source_data))))
  (entity-fields :id :type :date_created :source_data))
