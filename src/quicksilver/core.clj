(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.cookies]
            [ring.middleware.session]
            [ring.middleware.anti-forgery]
            [korma.core :refer [select where limit order]]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [compojure.coercions :refer [as-int]]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [quicksilver.config :refer [config]]
            [quicksilver.redis :as redis :refer [wcar*]]
            [quicksilver.entities :refer [old-widgets-map]]
            [quicksilver.slack :as slack]
            [quicksilver.widgets :as widgets]
            [quicksilver.websockets :as websockets]
            [quicksilver.routes :as routes]
            [quicksilver.web.auth]
            [quicksilver.web.widgets]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [org.httpkit.server :refer [run-server]]))

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

(defn get-widget-handler [{{widget-id :id} :route-params :as req}]
  (-> (widgets/get-widget widget-id)
      ((fn [widget] (match widget
          {:type "random-text"} (widgets/random-text widget)
          {:type "static-text"} (widgets/static-text widget)
          {:type "periodic-text"} (widgets/periodic-text widget)
          nil {:error "not found"}
          :else {:error "unknown widget type"})))
      (add-websockets-endpoint widget-id)
      wrap-json-response))

(defn get-text-handler [{{msg-type :msg-type} :route-params :as req}]
  (get-widget-handler (assoc-in req [:route-params :id]
                        (get old-widgets-map msg-type -1))))

(def web-routes
  (wrap-routes
    (routes
      (GET "/" [] (wrap-json-response "Hello World"))
      (context quicksilver.web.auth/url []
        (GET "/" [] quicksilver.web.auth/handler)
        (POST "/" [] quicksilver.web.auth/post-handler)
        (GET quicksilver.web.auth/logout-url [] quicksilver.web.auth/logout)
        (GET [quicksilver.web.auth/token-url, :token #"[0-9A-Za-z]+"]
              [] quicksilver.web.auth/token-handler))
      (context quicksilver.web.widgets/url []
        (GET "/"                                [] quicksilver.web.widgets/handler)
        (GET quicksilver.web.widgets/widget-url [] quicksilver.web.widgets/widget-handler)))

    #(-> %
          ring.middleware.anti-forgery/wrap-anti-forgery
          ring.middleware.session/wrap-session)))

(defroutes api-routes
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack/text-handler))
  (GET websockets/url [] websockets/ws-handler)
  (GET "/redis" [] (wcar* (redis/ping)))
  (context "/widgets" []
    (GET ["/:id", :id #"[0-9]+"] [id :<< as-int :as r] (get-widget-handler (assoc-in r [:route-params :id] id)))))

(defroutes all-routes
  web-routes
  api-routes)

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
