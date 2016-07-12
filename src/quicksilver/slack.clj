(ns quicksilver.slack
  (:gen-class)
  (:require [quicksilver.entities :as entities :refer [messages widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [quicksilver.config :refer [config]]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :refer [->camelCaseString]]))

(defn get-msg [widget-id]
  (-> (entities/get-widget-message widget-id})
      :text))

(defn check-token [token]
  (or (and (not token) (config :debug))
    (let [slack-tokens (set (filter some? (clojure.string/split (config :slack-tokens) #",")))]
      (contains? slack-tokens token))))

(defn check-author [author]
  (or (and (not author) (config :debug))
    (let [slack-authors (set (filter some? (clojure.string/split (config :slack-authors) #",")))]
      (contains? slack-authors author))))

(defn get-head-text [raw-text]
  (-> raw-text
      (clojure.string/split #" ")
      (match
        [] ["" ""]
        [h] [h ""]
        [h & split-text] [h (clojure.string/join " " split-text)])))

(defn text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (get-head-text raw-text)
        widget (entities/get-widget-by-title msg-type)
        widget-id (:id widget)]

      (match [widget-id text (check-token token) (check-author author)]
        [nil _ _ _] "no access (unknown widget type)"
        [_ "" _ _] (str msg-type ": " (get-msg widget-id))
        [_ _ false _] "no access (unknown token)"
        [_ _ _ false] "no access (unknown user)"
        :else (-> ; TODO: check values for periodic-text
                  (entities/insert-message {:widget_id widget-id, :author author, :text text})
                  (:text)
                  (#(do
                     (put! websockets/push-chan {:subj :update-widget :widget-id widget-id})
                     (str "+" msg-type ": " %)))))))

(defn wrap-json-response [resp]
  (-> resp
      (json/write-str :key-fn ->camelCaseString)
      (#(hash-map :body %
                  :headers {"Content-Type" "application/json; charset=utf-8"
                            "Access-Control-Allow-Origin" "*"}))))
