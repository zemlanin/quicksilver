(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :as k]))

; convert Jdbc4Array into vectors
(extend-protocol clojure.java.jdbc/IResultSetReadColumn
  org.postgresql.jdbc4.Jdbc4Array
  (result-set-read-column [pgobj metadata i]
    (vec (.getArray pgobj))))

(k/defentity messages
  (k/entity-fields :id :date_created :author :text :widget_id))

(defn pg-object->str [v & ks]
  (reduce #(assoc %1 %2 (if-some [pg-obj (%2 %1)] (.getValue pg-obj))) v ks))

(k/defentity widgets
  (k/transform (fn [v] (-> v
                        (pg-object->str :type :source_data))))
  (k/entity-fields :id :type :date_created :source_data :title))

(k/defentity teams
  (k/entity-fields :id :date_created :slack_id :slack_token :authors))

(defn get-widget [where]
  (-> (k/select widgets
        (k/where where)
        (k/limit 1)
        (k/order :date_created :DESC))
      (first)))

(defn update-widget [id fields]
  (k/update widgets
    (k/set-fields fields)
    (k/where {:id id})))

(defn get-team [where]
  (-> (k/select teams
        (k/where where)
        (k/limit 1)
        (k/order :date_created :DESC))
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
