(ns app
  (:require [vendor.effects :as e]))

(defn- eff_db [sql args]
  (fn [env] (env/perform :db [sql args])))

(defn- eff_fetch [url props]
  (fn [env] (env/perform :fetch [url props])))

(defn- send_message [data]
  (eff_fetch
   "https://api.telegram.org/bot~TG_TOKEN~/sendMessage"
   {:method "POST"
    :body (JSON/stringify data)
    :headers {"Content-Type" "application/json"}}))

(defn- match_rule [text regex]
  (and text
       (let [r (.match text (RegExp. regex))]
         (and r (.at r 1)))))

(defn- handle_ls_send [user_id items]
  (send_message
   {:chat_id user_id
    :text (->
           (.map items (fn [x] (JSON/parse x.document)))
           (.reduce (fn [a x] (str a "\n- " x.topic)) "Your subscriptions:"))}))

(defn- handle_ls [update]
  (if-let [user_id update?.message?.from?.id
           _ (= update?.message?.text "/ls")]
    (->
     (eff_db "SELECT * FROM subscriptions WHERE document->>'user_id' = ?" [user_id])
     (e/then (fn [items]
               (e/dispatch :handle_ls [user_id items]))))
    null))

(defn- handle_sub [update]
  (if-let [user_id update?.message?.from?.id
           topic (match_rule update?.message?.text "/sub\\s+(\\w+)")]
    (e/batch
     [(eff_db
       "INSERT INTO subscriptions (document) VALUES (?)"
       [(JSON/stringify {:topic topic :user_id user_id})])
      (send_message {:chat_id user_id :text "Subscription created"})])
    null))

(defn- handle_rm [update]
  (if-let [user_id update?.message?.from?.id
           topic (match_rule update?.message?.text "/rm\\s+(\\w+)")]
    (e/batch
     [(eff_db
       "DELETE FROM subscriptions WHERE document->>'topic' = ? AND document->>'user_id' = ?"
       [topic user_id])
      (send_message {:chat_id user_id :text "Subscriptions deleted"})])
    null))

(defn- handle_chat_update_send [message_id text r]
  (defn- get_unique_words [text]
    (->
     text
     (.split (RegExp. "[ ():;,.]+"))
     (.filter (fn [x] (>= x.length 3)))
     (.map (fn [x] (.toLowerCase x)))
     Set. Array/from))

  (defn- get_users_to_notify [r text]
    (let [topics (.reduce r (fn [map x] (.set map x.topic (.split x.user_ids ","))) (Map.))
          words (get_unique_words text)]
      (->
       words
       (.flatMap (fn [w] (or (.get topics w) [])))
       Set. Array/from)))

  (->
   (get_users_to_notify r text)
   (.map (fn [user_id]
           (send_message
            {:chat_id user_id
             :text (str "Topic updated: https://t.me/xofftop/" message_id)})))
   e/batch))

(defn- handle_chat_update [update]
  (if-let [text update?.message?.text
           user_id update?.message?.from?.id
           chat_id update?.message?.chat?.id
           message_id update?.message?.message_id
           _ (= chat_id -1002110559199)]
    (->
     (eff_db "SELECT document->>'topic' AS topic, group_concat(distinct(document->>'user_id')) AS user_ids FROM subscriptions GROUP BY topic" [])
     (e/then (fn [r]
               (e/dispatch :handle_chat_update [message_id text r]))))
    (FIXME (JSON/stringify update))))

(defn handle_event [key data]
  (case key
    :telegram (if-let [user_id data?.message?.from?.id
                       chat_id data?.message?.chat?.id]
                (if (= chat_id user_id)
                  (or
                   (handle_ls data)
                   (handle_sub data)
                   (handle_rm data)
                   (e/pure null))
                  (handle_chat_update data))
                (FIXME (JSON/stringify data)))
    :handle_ls (handle_ls_send (.at data 0) (.at data 1))
    :handle_chat_update (handle_chat_update_send (.at data 0) (.at data 1) (.at data 2))
    (FIXME key data)))

;; Infrastructure

(export-default
 {:fetch
  (fn [request env ctx]
    (->
     (.json request)
     (.then (fn [update]
              (let [world (->
                           env
                           e/attach_empty_effect_handler
                           (e/attach_eff :db
                                         (fn [args]
                                           (let [sql (.at args 0) sql_args (.at args 1)]
                                             (->
                                              env.DB (.prepare sql) (.bind (spread sql_args)) .run
                                              (.then (fn [x] x.results))))))
                           (e/attach_eff :fetch
                                         (fn [args]
                                           (let [url (.at args 0) props (.at args 1)]
                                             (->
                                              (.replaceAll url "~TG_TOKEN~" env.TG_TOKEN)
                                              (fetch props)))))
                           (e/attach_eff :dispatch
                                         (fn [args]
                                           (let [key (.at args 0) data (.at args 1)]
                                             (e/run_effect (handle_event key data) world))))
                           e/attach_log)]
                (e/run_effect (handle_event :telegram update) world))))
     (.catch console.error)
     (.then (fn [] (Response. (str "OK - " (Date.)))))))})
