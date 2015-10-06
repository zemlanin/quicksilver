(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.cookies]
            [korma.core :refer [select where limit order]]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [quicksilver.entities :refer [old-widgets-map]]
            [quicksilver.slack :as slack]
            [quicksilver.widgets :as widgets]
            [quicksilver.websockets :as websockets]
            [quicksilver.routes :as routes]
            [quicksilver.web.auth]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [org.httpkit.server :refer [run-server]]))

(defconfig config (io/resource "config/config.edn"))

(defdb db (postgres (:postgres (config))))

(defn wrap-json-response [resp]
  (-> resp
      (json/write-str :key-fn ->camelCaseString)
      (#(hash-map :body %
                  :headers {"Content-Type" "application/json; charset=utf-8"
                            "Access-Control-Allow-Origin" "*"}))))

(defn add-websockets-endpoint [resp widget-id]
  (if (:error resp)
    resp
    (assoc resp :ws {:url (routes/absolute websockets/url :subj "update-widget")
                      :conds {:widget-id widget-id}})))

(defn get-widget-handler [{{widget-id :id} :params}]
  (-> (widgets/get-widget (read-string widget-id))
      ((fn [widget] (match widget
          {:type "random-text"} (widgets/random-text widget)
          {:type "static-text"} (widgets/static-text widget)
          {:type "periodic-text"} (widgets/periodic-text widget)
          nil {:error "not found"}
          :else {:error "unknown widget type"})))
      (add-websockets-endpoint (read-string widget-id))
      wrap-json-response))

(defn get-text-handler [{{msg-type :msg-type} :params :as req}]
  (get-widget-handler (assoc-in req [:params :id]
                        (str (get old-widgets-map msg-type -1)))))

(defroutes all-routes
  (GET "/" [] (wrap-json-response "Hello World"))
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack/text-handler))
  (GET websockets/url [] websockets/ws-handler)
  (context quicksilver.web.auth/url []
    (GET "/" [] quicksilver.web.auth/handler)
    (POST "/" [] quicksilver.web.auth/post-handler)
    (GET [quicksilver.web.auth/token-url, :token #"[0-9A-Za-z]+"] [] quicksilver.web.auth/token-handler))
  (context "/widgets" []
    (GET ["/:id", :id #"[0-9]+"] [] get-widget-handler)))

(def my-app
  (-> all-routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ring.middleware.cookies/wrap-cookies))

(defn -main [& args]
  (let [handler (if (:debug (config))
                  (reload/wrap-reload #'my-app)
                  my-app)]
    (run-server handler (select-keys (config) [:port]))))
