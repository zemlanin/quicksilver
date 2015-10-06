(ns quicksilver.web.auth
  (:gen-class)
  (:require [nomad :refer [defconfig]]
            [clojure.java.io :as io]
            [korma.core :refer [select delete where limit insert values]]
            [hiccup.core :refer [html]]
            [quicksilver.entities :refer [auth-tokens]]
            [quicksilver.routes :refer [absolute]]
            [postal.core :refer [send-message]]))

(defconfig config (io/resource "config/config.edn"))
(defn base-url [] (:base-url (config)))

(defn fixed-length-password
  ([] (fixed-length-password 8))
  ([n]
     (let [chars (map char "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
           password (take n (repeatedly #(rand-nth chars)))]
       (reduce str password)))) 

(def url "/auth")
(def token-url "/:token")

(defn handler [{{auth "auth"} :cookies :as request}]
  (if auth ;; TODO: check in db
    "hi"
    (html
      [:form {:action url, :method "POST"}
        [:input {:type "email", :placeholder "email", :name "email"}]
        [:input {:type "submit"}]])))

(defn get-auth-token [id]
  (-> (select auth-tokens
        (where {:id id})
        (limit 1))
      (first)))

(defn insert-auth-token [id email]
  (-> (insert auth-tokens
        (values {:id id, :email email}))))

(defn post-handler [{{email "email"} :form-params :as request}]
  (delete auth-tokens
    (where {:email email}))

  (let [uniq (loop [cycle-uniq (fixed-length-password 30)]
                (if (get-auth-token cycle-uniq)
                    (recur (fixed-length-password 30))
                    cycle-uniq))]
    (insert-auth-token uniq email)
    (send-message {:from (str "auth@" (base-url))
                    :to email
                    :subject "Auth link for quicksilver"
                    :body (absolute (str url token-url) :token uniq)}))
  (html
    [:div "auth link is sent to email"]))

(defn token-handler [{{token :token} :route-params :as request}]
  (if (get-auth-token token)
    (do (delete auth-tokens (where {:id token}))
        {:body "you're in"
          :cookies {"auth" {:value token
                            :http-only true
                            :max-age (* 14 24 60 60)
                            :path "/"}}})
    "unknown or used token"))
