(ns app (:require [vendor.effects :as e]))

(defn- parse_rss_feed [url limit]
  (let [items []]
    (->
     (fetch url)
     (.then (fn [res]
              (let [text_buffer (atom "")]
                (->
                 (HTMLRewriter.)
                 (.on "entry"
                      {:element (fn [element]
                                  ;; (println "=== Element ===")
                                  (.push items {:links []}))})
                 (.on "entry *"
                      {:element (fn [element]
                                  ;; (println "child:" element.tagName (Array/from element.attributes))
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
                      {:element (fn [element]
                                  ;; (println "ITEMS:" items)
                                  (.push (.-links (.at items -1)) {:href (.getAttribute element "href")})
                                  (reset text_buffer "")
                                  (.onEndTag element
                                             (fn []
                                               (set! (.-name (.at (.-links (.at items -1)) -1)) (deref text_buffer))
                                               (reset text_buffer ""))))})
                 (.transform res)))))
     (.then (fn [x] (.arrayBuffer x)))
     (.then (fn [] (.splice items 0 limit))))))

(export-default
 {:fetch
  (fn [request env ctx]
    (->
    ;; "http://localhost:8000/razborfeed.html"
    ;; "http://localhost:8000/theaftertimes.html"
    ;; "https://t.me/s/razborfeed"
    ;; "https://t.me/s/bracket_devlog"
    ;; "https://t.me/s/izpodshtorki"
    ;; parse_tg_feed
     "https://developer.android.com/feeds/androidx-release-notes.xml"
     (parse_rss_feed 1)
    ;;  (.then (fn [items] (println "=== RESULT ===\n" (JSON/stringify items null 2))))
    ;;  (.catch console.error)
     (.then (fn [] (Response. "TEST_RESULT_OK")))))})