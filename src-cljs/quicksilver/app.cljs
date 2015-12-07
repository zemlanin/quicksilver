(ns ^:figwheel-always quicksilver.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [rum.core :as rum]
            [ajax.core :refer [GET POST]]
            [quicksilver.bus :as bus :refer [get-sub-chan]]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(defonce state (atom {}))

(defn initiate-state [])

(defn login-submit-handler [_]
  (POST "/auth" {:body {:email (-> @state :login-form :fields :email)}
                  :handler println
                  :error-handler #(swap! state assoc-in [:login-form :errors] (:response %))}))

(defn setup-listeners []
  (go-loop []
    (let [[_ msg] (async/<! (get-sub-chan :login-submit))]
      (login-submit-handler msg))
    (recur)))

(defn logged-in [{user :user logout-url :logout-url :as props}]
  [:div nil
    [:div (str "hi, " (:email user))]
    [:a {:href logout-url} "logout"]])

(defn field-change [form field]
  (fn [e] (swap! state assoc-in [form :fields field] (.. e -target -value))))

(defn login-form [{errors :errors fields :fields :as props}]
  [:div
    [:form {:action "#"
            :method "GET"
            :on-submit (bus/prevent-and-put :login-submit)}
      [:input { :type :email
                :placeholder :email
                :name :email
                :value (:email fields)
                :on-change (field-change :login-form :email)}]
      ;[:input {:type "hidden", :name "__anti-forgery-token", :value token}]
      [:input {:type "submit"}]]
    (when errors
      [:span {:style {:color :red}} errors])])

(rum/defc app-component < rum/reactive []
  (let [state (rum/react state)]
    [:div {}
      (if (:logged-in state)
        (logged-in state)
        (login-form (:login-form state)))
      [:hr]
      [:div {}
        (str state)]]))
