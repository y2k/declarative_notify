(ns core (:require ["../vendor/packages/effects/effects.2" :as e]
                   ["../vendor/packages/cf-xmlparser/xml_parser" :as xp]))

(defn- send_message [data]
  (e/fetch
   "https://api.telegram.org/bot~TG_TOKEN~/sendMessage"
   {:decoder :json
    :method "POST"
    :body (JSON.stringify data)
    :headers {"Content-Type" "application/json"}}))

(defn- match_rule [text regex]
  (and text
       (let [r (.match text (RegExp. regex))]
         (and r (.at r 1)))))

(defn- handle_ls_send [[user_id items]]
  (send_message
   {:chat_id user_id
    :text (->
           (.map items (fn [x] (JSON.parse x.document)))
           (.reduce (fn [a x] (str a "\n- " x.topic)) "Your subscriptions:"))}))

(defn- handle_ls [update]
  (if-let [user_id update?.message?.from?.id
           _ (= update?.message?.text "/ls")]
    (->
     (e/database "SELECT * FROM subscriptions WHERE document->>'user_id' = ?" [user_id])
     (e/next :handle_ls (fn [items] [user_id items])))
    null))

(defn- handle_sub [update]
  (if-let [user_id update?.message?.from?.id
           topic (match_rule update?.message?.text "/sub\\s+(\\w+)")]
    (e/batch
     [(e/database
       "INSERT INTO subscriptions (document) VALUES (?)"
       [(JSON.stringify {:topic topic :user_id user_id})])
      (send_message {:chat_id user_id :text "Subscription created"})])
    null))

(defn- handle_rm [update]
  (if-let [user_id update?.message?.from?.id
           topic (match_rule update?.message?.text "/rm\\s+(\\w+)")]
    (e/batch
     [(e/database
       "DELETE FROM subscriptions WHERE document->>'topic' = ? AND document->>'user_id' = ?"
       [topic user_id])
      (send_message {:chat_id user_id :text "Subscriptions deleted"})])
    null))

(defn- handle_telegram_default [update]
  (if-let [user_id update?.message?.from?.id]
    (send_message
     {:chat_id user_id
      :text "/ls - your subscriptions"})
    null))

(defn- handle_chat_update_send [[message_id text r]]
  (defn- get_unique_words [text]
    (->
     text
     (.split (RegExp. "[ ():;,.]+"))
     (.filter (fn [x] (>= x.length 3)))
     (.map (fn [x] (.toLowerCase x)))
     Set. Array.from))

  (defn- get_users_to_notify [r text]
    (let [topics (.reduce r (fn [map x] (.set map x.topic (.split x.user_ids ","))) (Map.))
          words (get_unique_words text)]
      (->
       words
       (.flatMap (fn [w] (or (.get topics w) [])))
       Set. Array.from)))

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
     (e/database "SELECT document->>'topic' AS topic, group_concat(distinct(document->>'user_id')) AS user_ids FROM subscriptions GROUP BY topic" [])
     (e/next :handle_chat_update (fn [r] [message_id text r])))
    (FIXME (JSON.stringify update))))

(defn handle_event [key data]
  (case key
    :telegram (if-let [user_id data?.message?.from?.id
                       chat_id data?.message?.chat?.id]
                (if (= chat_id user_id)
                  (or
                   (handle_ls data)
                   (handle_sub data)
                   (handle_rm data)
                   (handle_telegram_default data)
                   (e/pure null))
                  (handle_chat_update data))
                (FIXME (JSON.stringify data)))
    :handle_ls (handle_ls_send data)
    :handle_chat_update (handle_chat_update_send data)
    (FIXME key data)))

                  ;; Infrastructure

(defn parse_tg_feed [body]
  (xp/parse_with_dsl
   body
   [:div.tgme_widget_message_bubble
    [:div.tgme_widget_message_text :inner_text]
    [:a.tgme_widget_message_date :href]]))

(defn parse_rss_feed [body limit]
  (xp/parse_with_dsl
   body
   [:entry
    [:link :href]
    [:updated :inner_text]
    [:id :inner_text]
    [:title :inner_text]
    [:content [:li
               [:a :href]
               [:a :inner_text]]]]))
