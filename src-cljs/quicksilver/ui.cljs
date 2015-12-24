(ns ^:figwheel-always quicksilver.ui
  (:require [rum.core :as rum]
            [datascript.core :as d]
            [quicksilver.bus :as bus]
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
        user-ref (d/q '[:find ?e .
                        :where [?e :user/me true]]
                    db)
        user (d/entity db user-ref)]
    [:div {}
      (when user
        (if (:email user)
          (logged-in user)
          (get-form login-form conn :login)))
      [:hr]
      [:ul {}
        (map #(vector :li {} (str (d/touch (d/entity db (first %)))))
          (d/q '[:find ?e
                  :where [?e _ _]]
              db))]]))

(defn mount [conn]
  (rum/mount
    (app-component conn)
    (sel1 :#app)))
