(ns quicksilver.core
  (:require [ring.middleware.reload :as reload]
            [korma.core :refer :all]
            [korma.db :refer :all]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [clojure.set :refer [rename-keys]]
            [org.httpkit.server :refer [run-server]]))

(defdb db (postgres {:db "quicksilver"
                     :user "zem"
                     :password ""}))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :date_created :author :type :text))

(defn get-text-handler [{{text-type :text-type} :params}]
  (:text (first (select messages
                  (where {:type text-type})
                  (limit 1)
                  (order :date_created :DESC)))))

(defroutes all-routes
  (GET "/" [] "Hello World")
  (context "/text" []
    (GET "/:text-type" [text-type] get-text-handler)))

(defn in-dev? [args] true)

(defn -main [& args]
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (println "running server at http://localhost:8080")
    (run-server handler {:port 8080})))
