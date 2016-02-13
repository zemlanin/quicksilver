(ns quicksilver.slack
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :refer [messages slack-tokens widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [quicksilver.config :refer [config]]))

(defn get-msg [widget-id]
  (-> (select messages
        (where {:widget_id widget-id})
        (limit 1)
        (order :date_created :DESC))
      (first)
      :text))

(defn get-widget [widget-id]
  (-> (select widgets
        (where {:id widget-id})
        (limit 1))
      (first)))

(defn get-widget-by-title [title]
  (-> (select widgets
        (where {:title title})
        (limit 1))
      (first)))

(defn check-token [token]
  (or (and (not token) (config :debug))
    (-> (select slack-tokens
          (where {:token token})
          (limit 1))
        (empty?)
        (not))))

(def authors #{"a.verinov"
               "a.kuzmenko"
               "r.mamedov"
               "s.taran"
               "i.mozharovsky"
               "emarchenko"})

(defn text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (clojure.string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (clojure.string/join " " split-text)]))
        widget (get-widget-by-title msg-type)
        widget-id (:id widget)]

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
                     (put! websockets/push-chan {:subj :update-widget :widget-id widget-id})
                     (str "+" msg-type ": " %)))))))
