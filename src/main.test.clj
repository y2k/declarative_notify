(ns _ (:require [xml_parser :as xml]))

(export-default
 {:fetch
  (fn [request env ctx]
    (->
    ;; "https://t.me/s/razborfeed"
    ;; "https://t.me/s/bracket_devlog"
    ;; "https://t.me/s/izpodshtorki"
    ;; parse_tg_feed
     "https://developer.android.com/feeds/androidx-release-notes.xml"
     (xml/parse_rss_feed 1)
    ;;  (.then (fn [items] (println "=== RESULT ===\n" (JSON/stringify items null 2))))
    ;;  (.catch console.error)
     (.then (fn [items] (Response. (JSON/stringify items null 2))))))})