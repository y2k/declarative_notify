(__unsafe_insert_js "import * as e from './vendor/effects.js';")

(defn- eff_db [sql args]
  (fn [env]
    (->
     env.DB
     (.prepare sql)
     (.bind (spread args))
     (.run)
     (.then (fn [x] x.results)))))

(defn- eff_fetch [url props]
  (fn [env]
    (->
     (.replaceAll url "~TG_TOKEN~" env.TG_TOKEN)
     (fetch props))))

(defn- send_message [data]
  (eff_fetch
   "https://api.telegram.org/bot~TG_TOKEN~/sendMessage"
   {:method "POST"
    :body (JSON/stringify data)
    :headers {"Content-Type" "application/json"}}))

(defn- get_users_to_notify [r text]
  (defn- get_unique_words [text]
    (->
     text
     (.split (RegExp. "[ ():;,.]+"))
     (.filter (fn [x] (>= x.length 3)))
     (.map (fn [x] (.toLowerCase x)))
     Set. Array/from))

  (let [topics (.reduce r (fn [map x] (.set map x.topic (.split x.user_ids ","))) (Map.))
        words (get_unique_words text)]
    (->
     words
     (.flatMap (fn [w] (or (.get topics w) [])))
     Set. Array/from)))

(defn handle [update]
  (if-let [text update?.message?.text
           user_id update?.message?.from?.id
           chat_id update?.message?.chat?.id
           message_id update?.message?.message_id]
    (if (= "/ls" text)
      (->
       (eff_db "SELECT * FROM subscriptions WHERE content->>'user_id' = ?" [user_id])
       (e/then
        (fn [items]
          (let [message (->
                         (.map items (fn [x] (JSON/parse x.content)))
                         (.reduce (fn [a x] (str a "\n- " x.topic)) "Your subscriptions:"))]
            (send_message {:chat_id user_id :text message})))))
      (if (.startsWith text "/sub ")
        (let [topic (.substring text 5)]
          (e/batch
           [(eff_db
             "INSERT INTO subscriptions (content) VALUES (?)"
             [(JSON/stringify {:topic topic :user_id user_id})])
            (send_message {:chat_id user_id :text "Subscription created"})]))
        (if (.startsWith text "/rm ")
          (let [topic (.substring text 4)]
            (e/batch
             [(eff_db
               "DELETE FROM subscriptions WHERE content->>'topic' = ?"
               [topic])
              (send_message {:chat_id user_id :text "Subscriptions deleted"})]))
          (if (= chat_id -1002110559199)
            (->
             (eff_db "SELECT content->>'topic' AS topic, group_concat(distinct(content->>'user_id')) AS user_ids FROM subscriptions GROUP BY topic" [])
             (e/then
              (fn [r]
                (->
                 (get_users_to_notify r text)
                 (.map (fn [user_id] (send_message {:chat_id user_id :text (str "Topic updated: https://t.me/xofftop/" message_id)})))
                 e/batch))))
            FIXME))))
    FIXME))

(export-default
 {:fetch
  (fn [request env ctx]
    (->
     (.json request)
     (.then (fn [update] (e/run_effect (handle update) env)))
     (.catch console.error)
     (.then (fn [] (Response. (str "Healthy - " (Date.)))))))})
