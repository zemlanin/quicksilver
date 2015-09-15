(ns quicksilver.core
  (:require [ring.middleware.reload :as reload]
            [korma.core :refer :all]
            [korma.db :refer :all]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [clojure.core.match :refer [match]]
            [org.httpkit.server :refer [run-server]]))

(defconfig config (io/resource "config/config.edn"))

(defdb db (postgres (:postgres (config))))

(defentity messages
  (prepare (fn [v] (rename-keys v {:date-created :date_created})))
  (transform (fn [v] (rename-keys v {:date_created :date-created})))
  (entity-fields :date_created :author :type :text))

(defn get-msg [msg-type]
  (-> (select messages
        (where {:type msg-type})
        (limit 1)
        (order :date_created :DESC))
      first
      :text))

(defn get-text-handler [{{msg-type :msg-type} :params}]
  (get-msg msg-type))

(def authors #{
  nil ; so lazy
  "a.verinov"})

(defn slack-text-handler [{{raw-text :text, token :token, author :user_name} :params}]
  (let [[msg-type text] (-> raw-text
                            (string/split #" ")
                            (match
                              [] ["" ""]
                              [msg-type] [msg-type ""]
                              [msg-type & split-text] [msg-type (apply str split-text)]))]

      (match [text token]
        ["" _] (str msg-type ": " (get-msg msg-type))
        [_ nil] (str msg-type ": " (get-msg msg-type))
        ; TODO: check for tokens and user_name
        :else (if (and (= token "lazy") (contains? authors author))
                (-> (insert messages
                      (values {:author (or author ""), :type msg-type, :text text}))
                    (:text)
                    (->> (str msg-type ": ")))
                "no access"))))

(defroutes all-routes
  (GET "/" [] "Hello World")
  (context "/text" []
    (GET  "/:msg-type" req get-text-handler)
    (POST "/slack" req slack-text-handler)))

(defn in-dev? [args] true)

(defn -main [& args]
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (println "running server at http://localhost:8080")
    (run-server handler {:port 8080})))
