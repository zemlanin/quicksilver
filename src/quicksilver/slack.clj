(ns quicksilver.slack
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :refer [messages slack-tokens]]
            [clojure.core.match :refer [match]]))

(defn get-msg [msg-type]
  (-> (select messages
        (where {:type msg-type})
        (limit 1)
        (order :date_created :DESC))
      (first)
      :text))

(defn check-token [token]
  (-> (select slack-tokens
        (where {:token token})
        (limit 1))
      (empty?)
      (not)))

(def authors #{
  "a.verinov"
  "alxpy"
  "r.mamedov"
  "s.taran"
  "ivan"
  "i.mozharovsky"
  "emarchenko"})

(defn text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (clojure.string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (clojure.string/join " " split-text)]))]

      (match [text (contains? authors author) (check-token token)]
        ["" _ _] (str msg-type ": " (get-msg msg-type))
        [_ _ false] "no access (unknown token)"
        [_ false _] "no access (unknown user)"
        :else (-> (insert messages
                    (values {:author author, :type msg-type, :text text}))
                  (:text)
                  (#(str "+" msg-type ": " %))))))
