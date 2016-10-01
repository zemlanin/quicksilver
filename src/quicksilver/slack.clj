(ns quicksilver.slack
  (:gen-class)
  (:require [quicksilver.entities :as entities :refer [messages widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [quicksilver.config :refer [config]]
            [clojure.spec :as s]))

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

(defn validate-source-data [widget source-data]
  (match widget
    {:type "random-text"} (s/valid? :quicksilver.widgets/random-text source-data)
    {:type "periodic-text"} (s/valid? :quicksilver.widgets/periodic-text source-data)
    :else false))

(defn source-handler [raw-text team-id token author]
  (let [[msg-type text] (get-head-text raw-text)
        src (try
              (clojure.edn/read-string text)
              (catch Exception e :error))]
    (match [(clojure.edn/read-string text) (check-command team-id token author msg-type)]
      [nil {:widget {:source_data nil}}] (str msg-type " has no source")
      [nil {:widget widget}] (str msg-type " source: " (:source_data widget))
      [:error _] ":warning: Invalid source. Use EDN format"
      [_ {:error reason}] (str "no access (" reason ")")
      [src {:widget {:source_data nil}}] (str msg-type " has no source")
      [src {:widget widget}] (if (validate-source-data widget src)
                              src
                              ;(entities/update-widget (:id widget) {:source_data src})
                              (str ":warning: Invalid source. See existing one")))))

(defn message-handler [msg-type text team-id token author]
  (match [text (check-command team-id token author msg-type)]
    ["" {:widget widget}] (str msg-type ": " (get-msg (:id widget)))
    [_ {:error reason}] (str "no access (" reason ")")
    [_ {:widget widget}] (-> ; TODO: check values for periodic-text
                          (entities/insert-message {:widget_id (:id widget), :author author, :text text})
                          (:text)
                          (#(do
                             (put! websockets/push-chan {:subj :update-widget :widget-id (:id widget)})
                             (str "+" msg-type ": " %))))))

(defn text-handler [{{raw-text :text, token :token, author :user_name, team-id :team_id} :params}]
  (let [[head-text text] (get-head-text raw-text)]
    (match head-text
      ;"!source" (source-handler text team-id token author)
      (cmd :guard #(clojure.string/starts-with? % "!")) "!command not found"
      :else (message-handler head-text text team-id token author))))
