(ns ^:figwheel-always quicksilver.server
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [ajax.core :refer [GET POST DELETE]]
            [ajax.edn :refer [edn-response-format]]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]))

(defn whoami [handler]
  (GET "/api/whoami" {:response-format (edn-response-format)
                      :handler handler
                      :error-handler println}))

(defn auth-token [token handler]
  (POST (str "/api/auth/" token) {:response-format (edn-response-format)
                                  :handler handler
                                  :error-handler println}))

(defn auth-login [email handler error-handler]
  (POST "/api/auth" {:params {:email email}
                      :response-format (edn-response-format)
                      :handler handler
                      :error-handler error-handler}))

(defn auth-logout [handler]
  (DELETE "/api/auth" {:response-format (edn-response-format)
                        :handler handler
                        :error-handler println}))
