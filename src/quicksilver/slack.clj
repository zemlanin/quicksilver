(ns quicksilver.slack
  (:gen-class)
  (:require [quicksilver.entities :as entities :refer [messages widgets]]
            [quicksilver.websockets :as websockets]
            [clojure.core.async :as async :refer [put!]]
            [clojure.core.match :refer [match]]
            [clojure.data.json :as json]
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
    (match [src (check-command team-id token author msg-type)]
      [nil {:widget {:source_data nil}}] (str msg-type " has no source")
      [nil {:widget {:source_data prev}}] (str msg-type " source: `" prev "`")
      [:error _] ":warning: Invalid source. Use EDN format"
      [_ {:error reason}] (str "no access (" reason ")")
      [_ {:widget {:source_data nil}}] (str msg-type " has no source")
      [_ {:widget widget}] (if (validate-source-data widget src)
                              (do
                                (entities/update-widget (:id widget) {:source_data src})
                                (str ":ok_hand: `" (:source_data widget) "` -> `" src "`"))
                              (str ":warning: Invalid source. Check existing one")))))

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
      "!source" (source-handler text team-id token author)
      (cmd :guard #(clojure.string/starts-with? % "!")) "!command not found"
      :else (message-handler head-text text team-id token author))))
