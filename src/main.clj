(ns app (:require [vendor.effects :as e]))

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

(defn- handle_ls_send [[user_id items]]
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

(defn- handle_chat_update_send [[message_id text r]]
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
    :handle_ls (handle_ls_send data)
    :handle_chat_update (handle_chat_update_send data)
    (FIXME key data)))

;; Infrastructure

(defn- parse_tg_feed [url]
  (let [items []]
    (->
     (fetch url)
     (.then (fn [res]
              (let [text_builder (atom "") global_tb (atom "")]
                (->
                 (HTMLRewriter.)
                 (.on "div.tgme_widget_message *"
                      {:element (fn [element]
                                  (let [tag element.tagName clazz (or (.getAttribute element "class") "")]
                                    (if (and (= tag "div") (.startsWith clazz "tgme_widget_message_bubble"))
                                      (do
                                        (reset global_tb "")
                                        (.push items {})
                                        (.onEndTag element
                                                   (fn []
                                                     (set! (.-text (.at items -1)) (deref global_tb))
                                                     (reset global_tb "")))) null)
                                    (if (and (= tag "a") (.startsWith clazz "tgme_widget_message_date"))
                                      (if (= 0 items.length) null
                                          (set! (.-url (.at items -1)) (.getAttribute element "href"))) null)))
                       :text (fn [t]
                               (reset text_builder (str (deref text_builder) t.text))
                               (if t.lastInTextNode
                                 (do
                                   (if (not= "" (.trim (deref text_builder)))
                                     (reset global_tb (str (deref global_tb) (deref text_builder)))
                                     null)
                                   (reset text_builder ""))
                                 null))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] items)))))

(export-default
 {:scheduled
  (fn [event env ctx]
    (println "Started")
    (.waitUntil
     ctx
     (->
;; "http://localhost:8000/razborfeed.html"
;; "http://localhost:8000/theaftertimes.html"
;; "https://t.me/s/razborfeed"
;; "https://t.me/s/bracket_devlog"
      "https://t.me/s/izpodshtorki"
      parse_tg_feed
      (.then (fn [items] (println "RESULT: " items)))
      (.catch console.error))))
  :fetch
  (fn [request env ctx]
    (->
     (.json request)
     (.then (fn [update]
              (let [world (->
                           env
                           e/attach_empty_effect_handler
                           (e/attach_eff :db
                                         (fn [[sql sql_args]]
                                           (->
                                            env.DB (.prepare sql) (.bind (spread sql_args)) .run
                                            (.then (fn [x] x.results)))))
                           (e/attach_eff :fetch
                                         (fn [[url props]]
                                           (->
                                            (.replaceAll url "~TG_TOKEN~" env.TG_TOKEN)
                                            (fetch props))))
                           (e/attach_eff :dispatch
                                         (fn [[key data]]
                                           (e/run_effect (handle_event key data) world)))
                           e/attach_log)]
                (e/run_effect (handle_event :telegram update) world))))
     (.catch console.error)
     (.then (fn [] (Response. (str "OK - " (Date.)))))))})
