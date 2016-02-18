(ns quicksilver.github
  (:gen-class)
  (:require [quicksilver.config :refer [config]]
            [ring.util.response :refer [redirect]]
            [org.httpkit.client :as http]))

(defn auth-handler [{{code :code} :params}]
  (let [access-token-resp @(http/post "https://github.com/login/oauth/access_token"
                              {:query-params {:code code
                                              :client_id (config :github :client-id)
                                              :client_secret (config :github :client-secret)}
                                :as :text})
        access-token (-> access-token-resp :body)]
    ; TODO: add redirect base url to config (or move static to the same domain)
    (redirect (str "http://localhost:8000/?" access-token))))
