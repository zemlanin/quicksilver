(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [korma.core :refer [select where limit order]]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [quicksilver.entities :refer [messages]]
            [quicksilver.slack :as slack]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [org.httpkit.server :refer [run-server]]))

(defconfig config (io/resource "config/config.edn"))

(defdb db (postgres (:postgres (config))))

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

(defroutes all-routes
  (GET "/" [] (wrap-json-response "Hello World"))
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack/text-handler))
  (context "/extras" []
    (GET "/ready" [] get-ready-handler)))

(defn -main [& args]
  (let [handler (if (:debug (config))
                  (reload/wrap-reload (site #'all-routes))
                  (site all-routes))]
    (run-server handler (select-keys (config) [:port]))))
