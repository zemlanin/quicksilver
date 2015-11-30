(ns quicksilver.views.auth
  #?(:cljs (:require [quicksilver.bus :as bus])))

(defn logged-in [{user :user logout-url :logout-url :as props}]
  [:div nil
    [:div (str "hi, " (:email user))]
    [:a {:href logout-url} "logout"]])

(defn login-form [{errors :errors url :url token :token :as props}]
  [:div
    [:form {:action "#"
            :method "GET"
            #?@(:cljs [:onSubmit (bus/prevent-and-put :login-submit)])}
      [:input {:type "email", :placeholder "email", :name "email"}]
      [:input {:type "hidden", :name "__anti-forgery-token", :value token}]
      [:input {:type "submit"}]]
    (when errors
      [:span {:style {:color :red}} errors])])
