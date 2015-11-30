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
            [postal.core :refer [send-message]]
            [quicksilver.views.auth :as views]
            [ring.util.response :refer [redirect]]))

(def url "/auth")
(def token-url "/:token")
(def logout-url "/out")

(defn fixed-length-password
  ([] (fixed-length-password 8))
  ([n]
   (let [chars (map char "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
          password (take n (repeatedly #(rand-nth chars)))]
     (reduce str password))))

(defn get-session-user [session-id]
  (if-not session-id
    nil
    (-> (select sessions
          (with users)
          (where {:id session-id}))
        (first))))

(defn handler [{{{auth :value} "auth"} :cookies form-errors :form-errors :as request}]
  (if-let [user (get-session-user auth)]
    ; TODO: replace with redirect to page w/ auth header
    (let [init-data {:user user
                      :logout-url (absolute (str url logout-url))}]
      (html
        [:div {:id :header
                :data-init init-data}
          (views/logged-in init-data)]))
    (let [init-data { :errors form-errors
                      :url url
                      :token *anti-forgery-token*}]
      (html
        [:div {:id :login-form
                :data-init init-data}
          (views/login-form init-data)]
        [:script {:src "/static/js/compiled/quicksilver.js"}]))))

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

(defn post-handler [{{email "email"} :form-params :as request}]
  (if-not (validate-email email)
    (handler (assoc request :form-errors "invalid email"))
    (let [token (fixed-length-password 30)]
      (if-let [auth-value (insert-auth-token token email)]
        (do
          (send-message (config :email)
            {:from (str "auth@" (config :base-url))
              :to email
              :subject "Auth link for quicksilver"
              :body (absolute (str url token-url) :token token)})
          (html
            [:div "auth link is sent to email (expires in 10 minutes)"]))
        (handler (assoc request :form-errors "something wrong"))))))

(defn insert-session [user-id session-id]
  (insert sessions
    (values {:id session-id, :user-id user-id})))

(defn get-or-create-user [email]
  (if-let [user (get-user email)]
    user
    (insert users
      (values {:email email}))))

(defn token-handler [{{token :token} :route-params :as request}]
  (if-let [auth-token (get-auth-token token)]
    (let [user (get-or-create-user (:email auth-token))
          session-id (fixed-length-password 30)]
      (delete-auth-token token)
      (insert-session (:id user) session-id)
      {:body "you're in"
        :cookies {"auth" {:value session-id
                          :http-only true
                          :max-age (* 14 24 60 60)
                          :path "/"}}})
    (redirect (absolute url))))

(defn logout [{{{auth :value} "auth"} :cookies :as request}]
  (delete sessions (where {:id auth}))
  {:body "logged out"
    :cookies {"auth" {:value ""
                      :http-only true
                      :max-age 0
                      :path "/"}}})
