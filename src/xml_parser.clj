(defn parse_tg_feed [body]
  (let [items []]
    (->
     (Promise/resolve (Response. body))
     (.then (fn [res]
              (let [text_buffer (atom "")]
                (->
                 (HTMLRewriter.)
                 (.on "div.tgme_widget_message_bubble"
                      {:element (fn [element] (.push items {}))})
                 (.on "div.tgme_widget_message_bubble *"
                      {:element (fn [element]
                                  (cond
                                    (= null (.getAttribute element "class")) null
                                    (.startsWith (.getAttribute element "class") "tgme_widget_message_text")
                                    (do
                                      (reset text_buffer "")
                                      (.onEndTag element (fn []
                                                           (.push items (assoc (.pop items) :text (deref text_buffer)))
                                                           (reset text_buffer ""))))
                                    (.startsWith (.getAttribute element "class") "tgme_widget_message_date")
                                    (set! (.-url (.at items -1)) (.getAttribute element "href"))
                                    :else null))
                       :text (fn [t]
                               (reset text_buffer (str (deref text_buffer) t.text)))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] items)))))

(comment

  ;; HTML
  [:div {:class "tgme_widget_message_bubble"}
   {:text [:div {:class "tgme_widget_message_text"} :inner_text]
    :url [:a {:class "tgme_widget_message_date"} :href]}]

  ;; RSS
  [:entry {}
   {:link [:link {} :href]
    :updated [:updated {} :inner_text]
    :id [:id {} :inner_text]
    :title [:title {} :inner_text]
    :links [:content {} {:url [:a {} :href]
                         :title [:a {} :inner_text]}]}]

  comment)

(defn parse_rss_feed [body limit]
  (let [items []]
    (->
     (Promise/resolve (Response. body))
     (.then (fn [res]
              (let [text_buffer (atom "")]
                (->
                 (HTMLRewriter.)
                 (.on "entry"
                      {:element (fn [element]
                                  (.push items {:links []}))})
                 (.on "entry *"
                      {:element (fn [element]
                                  (defn- update_text [key]
                                    (reset text_buffer "")
                                    (.onEndTag element (fn []
                                                         (.push items (assoc (.pop items) key (deref text_buffer)))
                                                         (reset text_buffer ""))))
                                  (case element.tagName
                                    :link (set! (.-url (.at items -1)) (.getAttribute element "href"))
                                    :updated (update_text :updated)
                                    :id (update_text :id)
                                    :title (update_text :title)
                                    null))
                       :text (fn [t]
                               (reset text_buffer (str (deref text_buffer) t.text)))})
                 (.on "entry content a"
                      {:element
                       (fn [element]
                         (.push (.-links (.at items -1)) {:href (.getAttribute element "href")})
                         (reset text_buffer "")
                         (.onEndTag element
                                    (fn []
                                      (set! (.-name (.at (.-links (.at items -1)) -1)) (deref text_buffer))
                                      (reset text_buffer ""))))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] (.splice items 0 limit))))))
