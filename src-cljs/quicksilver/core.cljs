(ns ^:figwheel-always quicksilver.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [datascript.core :as d]
            [quicksilver.ui :as ui]
            [quicksilver.server :as server]
            [cljs.core.async :as async]
            [quicksilver.bus :as bus :refer [get-sub-chan]]))

(enable-console-print!)

(defonce conn (d/create-conn))

(ui/mount conn)

(defn load-user [& _]
  (server/whoami
    (fn [user]
      (when user
        (d/transact! conn [(assoc user
                            :user/me true)])))))

(defn initiate-state []
  (if-let [token (get (re-matches #"/auth/([0-9A-Za-z]+)" (.. js/document -location -pathname)) 1)]
    (do
      (server/auth-token token load-user)
      (.pushState js/history #js{} "quicksilver" "/"))
    (load-user)))

(defn -setup-listeners []
  (go-loop []
    (let [[_ msg] (async/<! (get-sub-chan :login-submit))
          form (d/q '[:find ?e .
                      :where [?e :form/key :login]]
                  @conn)
          email (:field/email (d/entity @conn form))]
      (server/auth-login email
        (fn [{message :message}]
          (d/transact! conn [[:db/add form
                              :message message]]))
        (fn [{error :response}]
          (d/transact! conn [[:db/add form
                              :errors error]]))))
    (recur))
  (go-loop []
    (let [[_ [key field value]] (async/<! (get-sub-chan :field-change))
          form (d/q '[:find ?e .
                      :in $ ?k
                      :where [?e :form/key ?k]]
                  @conn key)]
      (d/transact! conn [[:db/add form
                          (keyword "field" (name field)) value]]))
    (recur))
  (go-loop []
    (let [[_ msg] (async/<! (get-sub-chan :logout))
          user (d/q '[:find ?e .
                      :where [?e :user/me true]]
                  @conn)]
      (go (server/auth-logout println))
      (d/transact! conn [[:db.fn/retractEntity user]]))
    (recur)))


(defonce setup-initiate-state
  (do
    (initiate-state)))

(defonce setup-listeners
  (do
    (-setup-listeners)))
