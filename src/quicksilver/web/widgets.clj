(ns quicksilver.web.widgets
  (:gen-class)
  (:require [korma.core :refer [select delete where limit insert values join with]]
            [hiccup.core :refer [html]]
            [ring.util.response :refer [redirect]]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.core.match :refer [match]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [schema.core :as s]
            [quicksilver.entities :refer [widgets]]
            [quicksilver.api.widgets :refer [data-schemas]]
            [quicksilver.routes :refer [absolute]]
            [quicksilver.web.auth :refer [get-session-user]]))

(defn get-user-widgets
  ([user-id] (select widgets (where {:user_id user-id})))
  ([user-id widget-id] (-> (select widgets
                              (where {:user_id user-id, :id widget-id}))
                           (first))))

(defn handler [{{user :user} :session :as request}]
  (if user
    {:body (get-user-widgets (:id_2 user))}
    {:status 403}))

(defn hashmap? [v]
  (instance? clojure.lang.PersistentArrayMap v))

(defn get-field-name [prefix k]
  (str prefix (when prefix "_") (name k)))

(defn get-data-field [schema field-name data]
  (match schema
    (_ :guard hashmap?) [:div field-name
                          [:input {:value (string/join "," (map #(get-field-name field-name (first %)) schema))
                                    :name field-name
                                    :type "hidden"}]
                          [:ul
                            (map
                              (fn [[k s]]
                                [:li (get-data-field s
                                      (get-field-name field-name k)
                                      (k data))])
                              schema)]]
    [s] [:div field-name ; TODO: wtf is s?
          [:ul
            [:input {:value (string/join "," (map #(str field-name "_" %) (range (count data))))
                      :name field-name
                      :type "hidden"}]
            (map-indexed
              (fn [i d]
                [:li (get-data-field s (get-field-name field-name (str i)) d) [:button "remove"]])
              data)]
          [:button "add"]]
    :else [:label field-name
            (condp = schema
              s/Int   [:input {:value data, :name field-name, :type "number"}]
              s/Str   [:input {:value data, :name field-name}]
                      [:input {:value data, :name field-name, :style "color: red"}])]))

(defn widget-handler  [{{{auth :value} "auth"} :cookies {widget-id :id} :route-params :as request}]
  (if-let [user (get-session-user auth)]
    (let [w (get-user-widgets (:id_2 user) (read-string widget-id))
          widget-type (:type w)
          source-data (json/read-str (:source-data w)
                                     :key-fn ->kebab-case-keyword)
          source-scheme ((keyword widget-type) data-schemas)]
      (html
        [:p (:id w) " / " widget-type]
        [:form
          (get-data-field source-scheme nil source-data)]))
    (redirect (absolute "/"))))
