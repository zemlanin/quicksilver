(ns quicksilver.core
  (:require [ring.middleware.reload :as reload]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [org.httpkit.server :refer [run-server, with-channel, on-close, on-receive, send!]])) ; httpkit is a server

(def channels-map (atom {}))

(defn get-chan [req]
  (@channels-map (get-in req [:params :chan])))

(defn upd-chan [req channel]
  (swap! channels-map update-in [(get-in req [:params :chan])]
    (if (get-chan req) inc (constantly 1))))

(defn chan-handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [data]
                          (upd-chan request channel)
                          (send! channel (str (get-chan request)))))))

(defroutes all-routes
  (GET "/" [] "Hello World")
  (GET "/ws/:chan" [chan] chan-handler)
  (GET "/:chan" [chan] (str (@channels-map chan))))

(defn in-dev? [args] true)

(defn -main [& args]
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (println "running server at http://localhost:8080")
    (run-server handler {:port 8080})))
