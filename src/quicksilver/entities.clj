(ns quicksilver.entities
  (:gen-class)
  (:require [korma.core :refer [defentity prepare transform entity-fields table]]
            [clojure.set :refer [rename-keys]]))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :date_created :author :type :text))

(defentity slack-tokens
  (table :slack_tokens)
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :date_created :token))
