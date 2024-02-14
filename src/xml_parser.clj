(defn parse_tg_feed [url]
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
