(ns _ (:require ["../vendor/effects/effects.2" :as e]
                ["../vendor/cf-xmlparser/xml_parser" :as xp]
                ["../src/core" :as c]))

(export-default
 {:fetch
  (fn [request env ctx]
    (cond
      (.includes request.url "/test/")
      (->
       (.text request)
       (.then (fn [x] (c/parse_rss_feed x 100)))
       (.then (fn [x] (Response. (JSON.stringify x nil 4)))))

      (.includes request.url "/test2/")
      (->
       (.text request)
       (.then (fn [x] (c/parse_tg_feed x)))
       (.then (fn [x] (Response. (JSON.stringify x nil 4)))))

      :else nil))})
