(ns quicksilver.web.widgets
  (:gen-class)
  (:require [korma.core :refer [select delete where limit insert values join with]]
            [hiccup.core :refer [html]]
            [ring.util.response :refer [redirect]]
            [quicksilver.entities :refer [widgets]]
            [quicksilver.routes :refer [absolute]]
            [quicksilver.web.auth :refer [get-session-user]]))

(def url "/control/widgets")
(def widget-url "/:id")

(defn get-user-widgets 
  ([user-id] (select widgets (where {:user_id user-id})))
  ([user-id widget-id] (-> (select widgets
                              (where {:user_id user-id, :id widget-id}))
                            (first))))

(defn handler [{{{auth :value} "auth"} :cookies :as request}]
  (if-let [user (get-session-user auth)]
    (html
      [:ul (map (fn [{id :id, type :type, source-data :source-data}]
                    [:a {:href (absolute (str url widget-url) :id id)}
                      [:li id " / " type " / " source-data]])
            (get-user-widgets (:id_2 user)))])
    (redirect (absolute quicksilver.web.auth/url))))

(defn widget-handler  [{{{auth :value} "auth"} :cookies {widget-id :id} :route-params :as request}]
  (if-let [user (get-session-user auth)]
    (html
      (let [w (get-user-widgets (:id_2 user) (read-string widget-id))]
            [:p (:id w) " / " (:type w) " / " (:source-data w)]))
    (redirect (absolute quicksilver.web.auth/url))))
