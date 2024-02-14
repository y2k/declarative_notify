(ns test
  (:require [xml_parser :as xml]
            [js.wrangler :as w]
            [js.fs.promises :as fs]))

;;     ;; parse_tg_feed

(defn assert [worker url path]
  (->
   (fs/readFile path "utf-8")
   (.then (fn [xml] (.fetch worker url {:method :post :body xml})))
   (.then (fn [x] (.text x)))
   (.then (fn [res]
            (let [exp_path (.replace path ".xml" ".json")]
              (->
               (fs/readFile exp_path "utf-8")
               (.then (fn [expected] (println "Test result:" (= expected res) "|" path)))
               (.catch (fn [] (fs/writeFile exp_path res)))))))))

(->
 (w/unstable_dev "bin/main.js")
 (.then (fn [worker]
          (->
           (assert worker "/test/"  "../test/xml_parser/androidx-release-notes.xml")
           (assert worker "/test2/" "../test/xml_parser/izpodshtorki.xml")
           (.then (fn [] (.stop worker)))))))
