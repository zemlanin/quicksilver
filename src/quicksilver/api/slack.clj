(ns quicksilver.api.slack
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :refer [messages slack-tokens old-widgets-map widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]))

(defn get-msg [widget-id]
  (-> (select messages
        (where {:widget_id widget-id})
        (limit 1)
        (order :date_created :DESC))
      (first)
      :text))

(defn get-widget [widget-id]
  (-> (select widgets
        (where {:id widget-id}))
      (first)))

(defn check-token [token]
  (-> (select slack-tokens
        (where {:token token})
        (limit 1))
      (empty?)
      (not)))

(def authors #{
  "a.verinov"
  "a.kuzmenko"
  "r.mamedov"
  "s.taran"
  "i.dazhuk"
  "i.mozharovsky"
  "emarchenko"})

(defn text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (clojure.string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (clojure.string/join " " split-text)]))
        widget-id (get old-widgets-map msg-type)]

      (match [widget-id text (contains? authors author) (check-token token)]
        [nil _ _ _] "no access (unknown widget type)"
        [_ "" _ _] (str msg-type ": " (get-msg widget-id))
        [_ _ _ false] "no access (unknown token)"
        [_ _ false _] "no access (unknown user)"
        :else (-> ; TODO: check values for periodic-text
                  (insert messages
                    (values {:author author, :widget_id widget-id, :text text, :type msg-type}))
                  (:text)
                  (#(do
                    (put! websockets/pings {:subj :update-widget :widget-id widget-id})
                    (str "+" msg-type ": " %)))))))
