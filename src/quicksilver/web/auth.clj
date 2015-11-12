(ns quicksilver.web.auth
  (:gen-class)
  (:require [quicksilver.config :refer [config]]
            [korma.core :refer [select delete where limit insert values join with]]
            [korma.db :refer [transaction]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [hiccup.core :refer [html]]
            [quicksilver.entities :refer [auth-tokens users sessions]]
            [quicksilver.routes :refer [absolute]]
            [crypto.password.scrypt :as password]
            [postal.core :refer [send-message]]))

(def url "/auth")
(def token-url "/:id/:token")
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

(defn get-auth-token [id]
  (-> (select auth-tokens
        (where {:id id})
        (limit 1))
      (first)))

(defn get-user [email]
  (-> (select users
        (where {:email email})
        (limit 1))
      (first)))

(defn insert-auth-token [hashed email]
  (-> (insert auth-tokens
        (values {:hash hashed, :email email}))))

(defn validate-email [email]
  (some? (re-matches #"[^@\s]+@[^@\s\.]+\.[^@\s]+" email)))

(defn post-handler [{{email "email"} :form-params :as request}]
  (if-not (validate-email email)
    (handler (assoc request :form-errors "invalid email"))
    (do
      (delete auth-tokens (where {:email email}))

      (let [token (fixed-length-password 30)
            hashed (encrypt token)
            id (:id (insert-auth-token hashed email))]
        (send-message (email-config)
                      {:from (str "auth@" (base-url))
                        :to email
                        :subject "Auth link for quicksilver"
                        :body (absolute (str url token-url) :id id :token token)}))
      (html
        [:div "auth link is sent to email"]))))

(defn insert-session [user-id session-id]
  (insert sessions
    (values {:id session-id, :user-id user-id})))

(defn get-or-create-user [email]
  (if-let [user (get-user email)]
    user
    (insert users
      (values {:email email}))))

(defn token-handler [{{id :id, token :token} :route-params :as request}]
  (let [auth-token (get-auth-token id)]
    (if (password/check (add-salt token) (:hash auth-token))
      (let [user (get-or-create-user (:email auth-token))
            session-id (fixed-length-password 30)]
        (transaction
          (delete auth-tokens (where {:id id}))
          (insert-session (:id user) session-id))
          {:body "you're in"
            :cookies {"auth" {:value session-id
                              :http-only true
                              :max-age (* 14 24 60 60)
                              :path "/"}}})
      "unknown or used token")))

(defn logout [{{{auth :value} "auth"} :cookies :as request}]
  (delete sessions (where {:id auth}))
  {:body "logged out"
    :cookies {"auth" {:value ""
                      :http-only true
                      :max-age 0
                      :path "/"}}})
