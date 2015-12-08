(ns ^:figwheel-always quicksilver.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [rum.core :as rum]
            [ajax.core :refer [GET POST DELETE]]
            [ajax.edn :refer [edn-response-format]]
            [quicksilver.bus :as bus :refer [get-sub-chan]]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(defonce state (atom {}))

(defn whoami []
  (GET "/api/whoami" {:response-format (edn-response-format)
                      :handler #(swap! state assoc-in [:whoami] %)
                      :error-handler println}))

(defn initiate-state []
  (if-let [token (get (re-matches #"/auth/([0-9A-Za-z]+)" (.. js/document -location -pathname)) 1)]
    (do
      (POST (str "/api/auth/" token) {:response-format (edn-response-format)
                                      :handler #(whoami)
                                      :error-handler println})
      (.pushState js/history #js{} "quicksilver" "/"))
    (whoami)))

(defn login-submit-handler [_]
  (POST "/api/auth" {:params {:email (-> @state :login-form :fields :email)}
                      :response-format (edn-response-format)
                      :handler #(swap! state assoc-in [:login-form :message] (:message %))
                      :error-handler #(swap! state assoc-in [:login-form :errors] (-> % :response :errors))}))

(defn logout []
  (go (DELETE "/api/auth" {:params {}
                            :response-format (edn-response-format)
                            :handler #(swap! state dissoc :whoami)
                            :error-handler println}))
  true)

(defn setup-listeners []
  (go-loop []
    (let [[_ msg] (async/<! (get-sub-chan :login-submit))]
      (login-submit-handler msg))
    (recur)))

(defn logged-in [{email :email :as props}]
  [:div nil
    [:div (str "hi, " email)]
    [:a {:onClick #(logout)} "logout"]])

(defn field-change [form field]
  (fn [e] (swap! state assoc-in [form :fields field] (.. e -target -value))))

(defn login-form [{errors :errors fields :fields message :message :as props}]
  [:div
    [:form {:action "#"
            :method "GET"
            :on-submit (bus/prevent-and-put :login-submit)}
      [:input { :type :email
                :placeholder :email
                :name :email
                :value (:email fields)
                :on-change (field-change :login-form :email)}]
      [:input {:type "submit"}]]
    (when message
      [:span {} message])
    (when errors
      [:span {:style {:color :red}} errors])])

(rum/defc app-component < rum/reactive []
  (let [state (rum/react state)]
    [:div {}
      (if (:whoami state)
        (logged-in (:whoami state))
        (login-form (:login-form state)))
      [:hr]
      [:div {}
        (str state)]]))
