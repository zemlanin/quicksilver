(ns ^:figwheel-always quicksilver.ui
  (:require [rum.core :as rum]
            [datascript.core :as d]
            [quicksilver.bus :as bus]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

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
        user-ref (d/q '[:find ?e .
                        :where [?e :user/me true]]
                    db)
        user (d/entity db user-ref)]
    [:div nil
      [:div nil
        (str "hi, " (:email user))
        " "
        [:a {:onClick (bus/prevent-and-put :logout)} "[logout]"]]
      [:a {:onClick (bus/prevent-and-put :load/widgets)} "widgets"]]))

(rum/defc gatekeeper-component < rum/reactive [conn]
  (let [db (rum/react conn)
        user-ref (d/q '[:find ?e .
                        :where [?e :user/me true]]
                    db)
        user (d/entity db user-ref)]
    (when user
      [:div {}
        (if (:email user)
          (app-component conn)
          (get-form login-form conn :login))
        [:hr]
        [:ul {}
          (map #(vector :li {} (str (d/touch (d/entity db (first %)))))
            (d/q '[:find ?e
                    :where [?e _ _]]
                db))]])))

(defn mount [conn]
  (rum/mount
    (gatekeeper-component conn)
    (sel1 :#app)))
