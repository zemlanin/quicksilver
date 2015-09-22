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
            [clj-time.core :as t]
            [clj-time.jdbc]
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
      (first)))

(defn wrap-json-response [resp]
  (-> resp
      json/write-str
      (#(hash-map :body %
                  :headers {"Content-Type" "application/json; charset=utf-8"
                            "Access-Control-Allow-Origin" "*"}))))

(defn get-text-handler [{{msg-type :msg-type} :params}]
  (-> (get-msg msg-type)
      (select-keys [:text])
      wrap-json-response))

(defn is-ready [[wait-after hours-delta]]
  (= wait-after (mod hours-delta 2)))

(defn get-ready-handler [req]
  (-> (get-msg "ready")
      ((juxt
            #(if (= "-" (:text %)) 1 0)
            #(-> %
                :date-created
                (t/interval (t/now))
                t/in-hours)))
      is-ready
      (if "ready" "wait")
      (#(hash-map :text %))
      wrap-json-response))

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

(defn slack-text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (clojure.string/join " " split-text)]))]

      (match [text (contains? authors author) (check-token token)]
        ["" _ _] (str msg-type ": " (:text (get-msg msg-type)))
        [_ _ false] "no access (unknown token)"
        [_ false _] "no access (unknown user)"
        :else (-> (insert messages
                    (values {:author author, :type msg-type, :text text}))
                  (:text)
                  (#(str "+" msg-type ": " %))))))

(defroutes all-routes
  (GET "/" [] (wrap-json-response "Hello World"))
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack-text-handler))
  (context "/extras" []
    (GET "/ready" [] get-ready-handler)))

(defn -main [& args]
  (let [handler (if (:debug (config))
                  (reload/wrap-reload (site #'all-routes))
                  (site all-routes))]
    (run-server handler (select-keys (config) [:port]))))
