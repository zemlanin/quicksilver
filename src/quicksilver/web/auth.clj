(ns quicksilver.web.auth
  (:gen-class)
  (:require [quicksilver.config :refer [config]]
            [korma.core :refer [select delete where limit insert values join with]]
            [korma.db :refer [transaction]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [hiccup.core :refer [html]]
            [quicksilver.entities :refer [auth-tokens users sessions]]
            [quicksilver.routes :refer [absolute]]
            [quicksilver.redis :as redis :refer [wcar*]]
            [crypto.password.scrypt :as password]
            [postal.core :refer [send-message]]
            [ring.util.response :refer [redirect]]))

(def url "/auth")
(def token-url "/:token")
(def logout-url "/out")

(defn base-url [] (:base-url (config)))
(defn email-config [] (:email (config)))

(defn fixed-length-password
  ([] (fixed-length-password 8))
  ([n]
     (let [chars (map char "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
           password (take n (repeatedly #(rand-nth chars)))]
       (reduce str password))))

(defn add-salt [v]
  (str v (:auth-salt (config))))

(defn encrypt [v]
  (password/encrypt (add-salt v)))

(defn get-session-user [session-id]
  (if-not session-id
    nil
    (-> (select sessions
          (with users)
          (where {:id session-id}))
        (first))))

(defn handler [{{{auth :value} "auth"} :cookies form-errors :form-errors :as request}]
  (if-let [user (get-session-user auth)]
    (html
      [:div (str "hi, " (:email user))]
      [:a {:href (absolute (str url logout-url))} "logout"])
    (html
      [:form {:action url, :method "POST"}
        [:input {:type "email", :placeholder "email", :name "email"}]
        [:input {:type "hidden", :name "__anti-forgery-token", :value *anti-forgery-token*}]
        [:input {:type "submit"}]]
      (when form-errors [:span {:style "color: red"} form-errors]))))

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
    (let [token (fixed-length-password 30)
          id (:id (insert-auth-token token email))]
      (if-let [auth-value (insert-auth-token token email)]
        (do
          (send-message (email-config)
            {:from (str "auth@" (base-url))
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
