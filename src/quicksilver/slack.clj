(ns quicksilver.slack
  (:gen-class)
  (:require [korma.core :refer [select where limit order insert values]]
            [quicksilver.entities :as entities :refer [messages slack-tokens widgets]]
            [quicksilver.websockets :as websockets]
            [quicksilver.widgets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [quicksilver.config :refer [config]]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :refer [->camelCaseString]]))

(defn get-msg [widget-id]
  (-> (select messages
        (where {:widget_id widget-id})
        (limit 1)
        (order :date_created :DESC))
      (first)
      :text))

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

(defn widgets-cmd
  "`/dash widgets` – list all widgets"
  []
  (->> (select widgets)
      (map #(hash-map
              :fields [{:title "id" :short true :value (:id %)}
                       {:title "title" :short true :value (:title %)}
                       {:title "value" :value (:text (quicksilver.widgets/match-widget-type %))}]))
      (hash-map :text "widgets" :attachments)))

(defn wrap-json-response [resp]
  (-> resp
      (json/write-str :key-fn ->camelCaseString)
      (#(hash-map :body %
                  :headers {"Content-Type" "application/json; charset=utf-8"
                            "Access-Control-Allow-Origin" "*"}))))

(defn dash-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[cmd text] (get-head-text raw-text)]
    (-> cmd
      (match
        "help" {:text (clojure.string/join "\n" (map #(:doc (meta %)) [#'widgets-cmd]))}
        "widgets" (widgets-cmd))
      (wrap-json-response))))
