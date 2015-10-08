(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :refer [defentity prepare transform entity-fields table belongs-to has-many]]
            [clojure.set :refer [rename-keys]]))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created, :widget-id :widget_id})))
  (transform (fn [v] (rename-keys v {:date_created :date-created, :widget_id :widget-id})))
  (entity-fields :id :date_created :author :type :text :widget_id))

(defentity slack-tokens
  (table :slack_tokens)
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :date_created :token))

(defn pg-object->str [v & ks]
  (reduce #(assoc %1 %2 (if-some [pg-obj (%2 %1)] (.getValue pg-obj))) v ks))

(def old-widgets-map {
  "duty" 1
  "default" 2
  "quote" 3
  "uno" 4
  "gif" 5
  "ready" 6})

(defentity widgets
  (prepare (fn [v] (rename-keys v {:date-created :date_created, :source-data :source_data})))
  (transform (fn [v] (-> v
                        (rename-keys {:date_created :date-created, :source_data :source-data})
                        (pg-object->str :type :source-data))))
  (entity-fields :id :type :date_created :source_data ))

(defentity auth-tokens
  (table :auth_tokens)
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :hash :date_created :email))

(defentity users
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :email :date_created))

(defentity sessions
  (prepare (fn [v] (rename-keys v {:date-created :date_created, :user-id :user_id})))
  (transform (fn [v] (rename-keys v {:date_created :date-created, :user_id :user-id})))
  (belongs-to users {:fk :user_id})
  (entity-fields :id :user_id :date_created))
