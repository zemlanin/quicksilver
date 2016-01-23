(ns quicksilver.core
  (:gen-class)
  (:require [ring.middleware.reload :as reload]
            [ring.middleware.params]
            [ring.middleware.cookies]
            [ring.middleware.session]
            [ring.middleware.session.cookie]
            [ring.middleware.anti-forgery]
            [ring.middleware.keyword-params]
            [ring.middleware.format]
            [ring.middleware.format-response]
            [ring.util.response]
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
            [quicksilver.web.auth]
            [quicksilver.web.widgets]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [camel-snake-kebab.core :refer [->camelCaseString]]
            [clj-time.core :as t]
            [clj-time.jdbc]
            [org.httpkit.server :refer [run-server]]))

(defdb db (postgres (:postgres (config))))

(extend-protocol cheshire.generate/JSONable
  org.joda.time.DateTime
  (to-json [t jg]
    (cheshire.generate/write-string jg (str t))))

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

(defn wrap-visited-site [handler]
  (fn [request]
    (let [response (handler request)
          new-session (if (:session response)
                        (assoc (:session response) :visited true)
                        (assoc (:session request) :visited true))]

      (assoc response :session new-session))))

(def index-response (ring.util.response/resource-response "index.html" {:root "public"}))

(defonce session-store
  (ring.middleware.session.cookie/cookie-store {:key (config :session-secret)}))

(defn wrap-session-user [handler]
  (fn [request]
    (let [session (-> request :session)
          auth-id (-> session :auth-id)
          user (quicksilver.web.auth/get-session-user auth-id)
          extended-session (if user
                            (assoc session :user user)
                            session)
          response (handler (assoc request :session extended-session))

          shrunk-session (when (:session response)
                            (dissoc (:session response) :user))]

      (if shrunk-session
        (assoc response :session shrunk-session)
        response))))


(def web-routes
  (wrap-routes
    (routes
      (GET "/" [] index-response)
      (GET ["/auth/:token" :token #"[0-9A-Za-z]+"] [] index-response))

    #(-> %
        wrap-visited-site)))

(defn wrap-joda-time [handler]
  (fn [request]
    (->> request
        handler
        (clojure.walk/prewalk
          #(if (instance? org.joda.time.DateTime %) (.toDate %) %)))))

(defn wrap-restful-format [handler]
  (ring.middleware.format/wrap-restful-format handler
    :formats [:json :edn]
    :response-options {:json {:key-fn ->camelCaseString}}))

(defroutes api-routes
  (wrap-routes
    (routes
      (context "/api" []
        (GET "/whoami" [] quicksilver.web.auth/whoami)
        (context "/auth" []
          (POST "/" [] quicksilver.web.auth/post-handler)
          (DELETE "/" [] quicksilver.web.auth/logout)
          (POST ["/:token" :token #"[0-9A-Za-z]+"] [] quicksilver.web.auth/token-handler))
        (context "/widgets" []
          (GET "/" [] quicksilver.web.widgets/handler)
          (GET ["/:id" :id #"[0-9]+"] [] quicksilver.web.widgets/widget-handler)))

      (context "/text" []
        (GET  "/:msg-type" [] get-text-handler)
        (POST "/slack" [] slack/text-handler))
      (GET websockets/url [] websockets/ws-handler)
      (context "/widgets" []
        (GET ["/:id", :id #"[0-9]+"] [id :<< as-int :as r] (get-widget-handler (assoc-in r [:route-params :id] id)))))

    #(-> %
        (wrap-joda-time)
        (wrap-restful-format))))

(defroutes all-routes
  (if (config :debug) (compojure.route/resources "/static/") {})
  web-routes
  api-routes)

(def my-app
  (-> all-routes
      wrap-session-user
      (ring.middleware.session/wrap-session
        {:store session-store
          :cookie-attrs {:max-age (* 30 24 3600)}})
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ring.middleware.cookies/wrap-cookies))

(def my-app-reload
  (-> my-app
      (reload/wrap-reload {:dirs ["src" #_"src-cljc"]})))

(defn -main [& args]
  (let [handler (if (config :debug) my-app-reload my-app)]
    (run-server handler {:port (config :port)})
    (when (config :debug) (println "server's running"))))
