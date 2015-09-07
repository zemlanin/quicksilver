(ns quicksilver.core
  (:require [ring.middleware.reload :as reload]
            [compojure.core :refer :all]
            [korma.core :refer :all]
            [korma.db :refer :all]
            [compojure.handler :refer [site]]
            [clojure.set :refer [rename-keys]]
            [org.httpkit.server :refer [run-server, with-channel, on-close, on-receive, send!]]))

(defdb db (postgres {:db "quicksilver"
                     :user "zem"
                     :password ""}))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :date_created :author :type :text))

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

(defn text-handler [request]
  (let [latest-text (select messages
                     (where
                        {:type (get-in request [:params :type] "uno")})
                     (limit 1)
                     (order :date_created :DESC))]
       (:text (first latest-text))))

(defroutes all-routes
  (GET "/" [] "Hello World")
  (GET "/text" [] text-handler)
  (GET "/ws/:chan" [chan] chan-handler)
  (GET "/:chan" [chan] (str (@channels-map chan))))

(defn in-dev? [args] true)

(defn -main [& args]
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (println "running server at http://localhost:8080")
    (run-server handler {:port 8080})))
