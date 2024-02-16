(defn parse_tg_feed [body]
  (let [items []]
    (->
     (Promise/resolve (Response. body))
     (.then (fn [res]
              (let [text_buffer (atom "")]
                (->
                 (HTMLRewriter.)
                 (.on "div.tgme_widget_message_bubble"
                      {:element (fn [element] (.push items [null null]))})
                 (.on "div.tgme_widget_message_bubble *"
                      {:element (fn [element]
                                  (cond
                                    (= null (.getAttribute element "class")) null

                                    (.startsWith (.getAttribute element "class") "tgme_widget_message_text")
                                    (do
                                      (reset text_buffer "")
                                      (.onEndTag element (fn []
                                                           (-> items (.at -1) (assoc! 0 (deref text_buffer)))
                                                           (reset text_buffer ""))))

                                    (.startsWith (.getAttribute element "class") "tgme_widget_message_date")
                                    (-> items (.at -1) (assoc! 1 (.getAttribute element "href")))

                                    :else null))
                       :text (fn [t]
                               (reset text_buffer (str (deref text_buffer) t.text)))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] items)))))

(comment

  [:div {:class "tgme_widget_message_bubble"}
   [:div {:class "tgme_widget_message_text"} :inner_text]
   [:a {:class "tgme_widget_message_date"} :href]]

  [:entry {}
   [:link {} :href]
   [:updated {} :inner_text]
   [:id {} :inner_text]
   [:title {} :inner_text]
   [:content {}
    [:a {} :href]
    [:a {} :inner_text]]]

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
                                  (.push items [null null null null []]))})
                 (.on "entry *"
                      {:element (fn [element]
                                  (defn- update_text [key]
                                    (reset text_buffer "")
                                    (.onEndTag element (fn []
                                                         (assoc! (.at items -1) key (deref text_buffer))
                                                         (reset text_buffer ""))))
                                  (case element.tagName
                                    :link (assoc! (.at items -1) 0 (.getAttribute element "href"))
                                    :updated (update_text 1)
                                    :id (update_text 2)
                                    :title (update_text 3)
                                    null))
                       :text (fn [t]
                               (reset text_buffer (str (deref text_buffer) t.text)))})
                 (.on "entry content a"
                      {:element
                       (fn [element]
                         (-> items (.at -1) (.at 4) (.push [null null]))
                         (-> items (.at -1) (.at 4) (.at -1) (assoc! 0 (.getAttribute element "href")))
                         (reset text_buffer "")
                         (.onEndTag element
                                    (fn []
                                      (-> items (.at -1) (.at 4) (.at -1) (assoc! 1 (deref text_buffer)))
                                      (reset text_buffer ""))))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] (.splice items 0 limit))))))
