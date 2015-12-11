(ns ^:figwheel-always quicksilver.ui
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [rum.core :as rum]
            [datascript.core :as d]
            [ajax.core :refer [GET POST DELETE]]
            [ajax.edn :refer [edn-response-format]]
            [quicksilver.bus :as bus :refer [get-sub-chan]]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(defn logged-in [{email :email :as props}]
  [:div nil
    [:div {} (str "hi, " email)]
    [:a {:onClick (bus/prevent-and-put :logout)} "logout"]])

(defn field-change [form field]
  (fn [e] ((bus/preventless-put :field-change) [form field (.. e -target -value)])))

(defn login-form [{key :form/key errors :errors email :field/email message :message :as props}]
  [:div
    [:form {:action "#"
            :method "GET"
            :on-submit (bus/prevent-and-put :login-submit)}
      [:input { :type :email
                :placeholder :email
                :name :email
                :value email
                :on-change (field-change key :email)}]
      [:input {:type "submit"}]]
    (when message
      [:span {} message])
    (when errors
      [:span {:style {:color :red}} (vals errors)])])

(defn get-form [form conn key]
  (let [db @conn
        form-state (d/q '[:find ?e .
                          :in $ ?k
                          :where [?e :form/key ?k]]
                      db key)]
    (if form-state
      (form (d/entity db form-state))
      (do
        (d/transact! conn [{:form/key key}])
        (form {:form/key key})))))

(rum/defc app-component < rum/reactive [conn]
  (let [db (rum/react conn)
        user (d/q '[:find ?e .
                    :where [?e :user/me true]]
                db)]
    [:div {}
      (if user
        (logged-in (d/entity db user))
        (get-form login-form conn :login))
      [:hr]
      [:ul {}
        (map #(vector :li {} (str (d/touch (d/entity db (first %)))))
          (d/q '[:find ?e
                  :where [?e _ _]]
              db))]]))
