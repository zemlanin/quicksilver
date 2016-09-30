(ns quicksilver.slack
  (:gen-class)
  (:require [quicksilver.entities :as entities :refer [messages widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [quicksilver.config :refer [config]]))

(defn get-msg [widget-id]
  (-> (entities/get-widget-message widget-id)
      :text))

(defn get-head-text [raw-text]
  (-> raw-text
      (clojure.string/split #" ")
      (match
        [] ["" ""]
        [h] [h ""]
        [h & split-text] [h (clojure.string/join " " split-text)])))

(defn check-command [team-id token author msg-type]
  (let [team (entities/get-team {:slack_id team-id :slack_token token})
        widget (and team (entities/get-widget {:team_id (:id team) :title msg-type}))]
    (cond
      (not team) {:error "team"}
      (not widget) {:error "widget"}
      (not (contains? (-> team :authors set) author)) {:error "user" :widget widget}
      :else {:widget widget})))

(defn text-handler [{{raw-text :text, token :token, author :user_name, team-id :team_id} :params}]
  (let [[msg-type text] (get-head-text raw-text)]
    (match [text (check-command team-id token author msg-type)]
      ["" {:widget widget}] (str msg-type ": " (get-msg (:id widget)))
      [_ {:error reason}] (str "no access (" reason ")")
      [_ {:widget widget}] (-> ; TODO: check values for periodic-text
                            (entities/insert-message {:widget_id (:id widget), :author author, :text text})
                            (:text)
                            (#(do
                               (put! websockets/push-chan {:subj :update-widget :widget-id (:id widget)})
                               (str "+" msg-type ": " %)))))))
