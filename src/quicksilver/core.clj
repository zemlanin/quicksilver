(ns quicksilver.core
  (:gen-class :main true)
  (:import [org.joda.time DateTime DateTimeZone ReadableInstant]
           [org.joda.time.format ISODateTimeFormat])
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [compojure.route]
            [compojure.coercions :refer [as-int]]
            [clojure.string :as string]
            [quicksilver.config :refer [config]]
            [quicksilver.slack :as slack]
            [quicksilver.widgets :as widgets]
            [quicksilver.websockets :as websockets]
            [quicksilver.routes :as routes]
            [quicksilver.entities :as entities]
            [quicksilver.github :as github]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [org.httpkit.server :refer [run-server]]))

(defdb db (postgres (config :postgres)))

(extend-protocol json/JSONWriter
  org.joda.time.DateTime
  (-write [in out]
    (.print out (str "\"" in "\""))))

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

(defn get-widget-handler [{{widget-id :id, title :title} :route-params :as req}]
  (let [widget (if widget-id
                  (entities/get-widget {:id (as-int widget-id)})
                  (entities/get-widget {:title title}))]
    (-> widget
        (widgets/match-widget-type)
        (add-websockets-endpoint (:id widget))
        wrap-json-response)))

(defroutes api-routes
  (context ["/:widgets" :widgets #"widgets|text"] []
    (GET ["/:id", :id #"[0-9]+"] [] get-widget-handler)
    (GET ["/:title", :title #"[a-z][a-z0-9_]+"] [] get-widget-handler))
  (context "/slack" []
    (POST "/text" [] slack/text-handler))
  (context "/github" []
    (GET "/auth" [] github/auth-handler))
  (GET websockets/url [] websockets/ws-handler))

(def my-app
  (-> api-routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(def my-app-reload
  (-> my-app
      (reload/wrap-reload {:dirs ["src"]})))

(defn -main [& args]
  (let [handler (if (config :debug) my-app-reload my-app)]
    (run-server handler {:port (read-string (config :port))})
    (when (config :debug) (println "server's running"))))
