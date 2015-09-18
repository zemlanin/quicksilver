(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [korma.core :refer :all]
            [korma.db :refer :all]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [org.httpkit.server :refer [run-server]]))

(defconfig config (io/resource "config/config.edn"))

(defdb db (postgres (:postgres (config))))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :date_created :author :type :text))

(defentity slack-tokens
  (table :slack_tokens)
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :id :date_created :token))

(defn get-msg [msg-type]
  (-> (select messages
        (where {:type msg-type})
        (limit 1)
        (order :date_created :DESC))
      (first)
      (:text)))

(defn get-text-handler [{{msg-type :msg-type} :params}]
  (->> (get-msg msg-type)
      (hash-map :text)
      json/write-str
      (hash-map
        :headers {
                  "Content-Type" "application/json; charset=utf-8"
                  "Access-Control-Allow-Origin" "*"}
        :body)))

(defn check-token [token]
  (-> (select slack-tokens
        (where {:token token})
        (limit 1))
      (empty?)
      (not)))

(def authors #{
  nil ; so lazy
  "a.verinov"})

(defn slack-text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (apply str split-text)]))]

      (match [text token]
        ["" _] (str msg-type ": " (get-msg msg-type))
        [_ nil] (str msg-type ": " (get-msg msg-type))
        ; TODO: check for tokens and user_name
        :else (if (and (contains? authors author) (check-token token))
                (-> (insert messages
                      (values {:author (or author ""), :type msg-type, :text text}))
                    (:text)
                    (->> (str msg-type ": ")))
                "no access"))))

(defroutes all-routes
  (GET "/" [] "Hello World")
  (context "/text" []
    (GET  "/:msg-type" req get-text-handler)
    (POST "/slack" req slack-text-handler)))

(defn -main [& args]
  (let [handler (if (:debug (config))
                  (reload/wrap-reload (site #'all-routes))
                  (site all-routes))]
    (run-server handler (select-keys (config) [:port]))))
