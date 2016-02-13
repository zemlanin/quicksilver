(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [korma.db :refer [defdb postgres]]
            [compojure.core :refer :all]
            [compojure.route]
            [compojure.coercions :refer [as-int]]
            [clojure.string :as string]
            [quicksilver.config :refer [config]]
            [quicksilver.entities :refer [old-widgets-map]]
            [quicksilver.api.slack :as slack]
            [quicksilver.api.widgets :as widgets]
            [quicksilver.websockets :as websockets]
            [quicksilver.routes :as routes]
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
      ((fn [widget]
        (match widget
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

(defroutes api-routes
  (context "/text" []
    (GET  "/:msg-type" [] get-text-handler)
    (POST "/slack" [] slack/text-handler))
  (GET websockets/url [] websockets/ws-handler)
  (context "/widgets" []
    (GET ["/:id", :id #"[0-9]+"] [id :<< as-int :as r] (get-widget-handler (assoc-in r [:route-params :id] id)))))

(def my-app
  (-> api-routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(def my-app-reload
  (-> my-app
      (reload/wrap-reload {:dirs ["src"]})))

(defn -main [& args]
  (let [handler (if (config :debug) my-app-reload my-app)]
    (run-server handler {:port (config :port)})
    (when (config :debug) (println "server's running"))))
