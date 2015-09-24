(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [korma.core :refer [select where limit order]]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [quicksilver.entities :refer [messages old-widgets-map]]
            [quicksilver.slack :as slack]
            [quicksilver.widgets :as widgets]
            [quicksilver.websockets :as websockets]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [clojure.core.async :as async :refer [put!]]
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

(defn get-widget-handler [{{widget-id :id} :params}]
  (-> (widgets/get-widget (read-string widget-id))
      ((fn [widget] (match widget
          {:type "random-text"} (widgets/random-text widget)
          {:type "static-text"} (widgets/static-text widget)
          {:type "periodic-text"} (widgets/periodic-text widget)
          nil {:error "not found"}
          :else {:error "unknown widget type"})))
      wrap-json-response))

(defn get-text-handler [{{msg-type :msg-type} :params :as req}]
  (get-widget-handler (assoc-in req [:params :id]
                        (str (get old-widgets-map msg-type -1)))))

(defn ping-handler [req]
  (put! websockets/pings {:subj :pong})
  (wrap-json-response "pong"))

(defroutes all-routes
  (GET "/" [] (wrap-json-response "Hello World"))
  (GET "/ping" [] ping-handler)
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack/text-handler))
  (GET  "/ws" [] websockets/ws-handler)
  (context "/widgets" []
    (GET ["/:id", :id #"[0-9]+"] [] get-widget-handler)))

(def my-app
  (-> all-routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(defn -main [& args]
  (let [handler (if (:debug (config))
                  (reload/wrap-reload #'my-app)
                  my-app)]
    (run-server handler (select-keys (config) [:port]))))
