(ns quicksilver.core
  (:require [compojure.core :refer :all]
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

(defroutes quicksilver
  (GET "/" [] "Hello World")
  (GET "/ws/:chan" [chan] chan-handler)
  (GET "/:chan" [chan] (str (@channels-map chan))))

(defn -main []
  (run-server quicksilver {:port 8080}))
