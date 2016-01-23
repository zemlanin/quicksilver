(ns quicksilver.web.auth
  (:gen-class)
  (:require [quicksilver.config :refer [config]]
            [korma.core :refer [select delete where limit insert values join with]]
            [korma.db :refer [transaction]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [hiccup.core :refer [html]]
            [quicksilver.entities :refer [users sessions]]
            [quicksilver.routes :refer [absolute]]
            [quicksilver.redis :as redis :refer [wcar*]]
            [postal.core :refer [send-message]]))

(defn fixed-length-password
  ([] (fixed-length-password 8))
  ([n]
   (let [chars (map char "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
          password (take n (repeatedly #(rand-nth chars)))]
     (reduce str password))))

(defn get-session-user [session-id]
  (when session-id
    (-> (select sessions
          (with users)
          (where {:id session-id}))
        (first)
        (dissoc :date-created))))

(defn insert-auth-token [token email]
  (let [auth-value {:token token :email email}]
    (when (= "OK" (wcar* (redis/setex (redis/key :auth-tokens token) 600 auth-value)))
      auth-value)))

(defn get-auth-token [token]
  (wcar* (redis/get (redis/key :auth-tokens token))))

(defn delete-auth-token [token]
  (wcar* (redis/del (redis/key :auth-tokens token))))

(defn get-user [email]
  (-> (select users
        (where {:email email})
        (limit 1))
      (first)))

(defn validate-email [email]
  (some? (re-matches #"[^@\s]+@[^@\s\.]+\.[^@\s]+" email)))

(defn whoami [{{user :user} :session :as request}]
  {:body user})

(defn post-handler [{{email :email} :body-params session :session :as request}]
  (if-not (:visited session)
    {:status 401 :body {:message "unknown session"}}
    (if-not (and email (validate-email email))
      {:status 400 :body {:email "invalid email"}}
      (let [token (fixed-length-password 30)]
        (if-let [auth-value (insert-auth-token token email)]
          (do
            (send-message (config :email)
              {:from (str "auth@" (config :base-url))
                :to email
                :subject "Auth link for quicksilver"
                :body (absolute "/auth/:token" :token token)})
            {:body {:message "auth link is sent to email (expires in 10 minutes)"}})
          {:status 500})))))

(defn insert-session [user-id session-id]
  (insert sessions
    (values {:id session-id, :user-id user-id})))

(defn get-or-create-user [email]
  (if-let [user (get-user email)]
    user
    (insert users
      (values {:email email}))))

(defn token-handler [{{token :token} :route-params session :session :as request}]
  (if-let [auth-token (get-auth-token token)]
    (let [user (get-or-create-user (:email auth-token))
          auth-id (fixed-length-password 30)]
      (delete-auth-token token)
      (insert-session (:id user) auth-id)
      {:body "you're in"
        :session (assoc session :auth-id auth-id)})
    {:body "hmm"}))

(defn logout [{session :session :as request}]
  (delete sessions (where {:id (:auth-id session)}))
  {:body "logged out"
    :session (dissoc session :auth-id)})
