(ns _ (:require ["../vendor/cf-xmlparser/xml_parser" :as xml]
                [js.wrangler :as w]
                [js.fs.promises :as fs]))

(defn assert [worker url path]
  (->
   (fs/readFile path "utf-8")
   (.then (fn [xml] (.fetch worker url {:method :post :body xml})))
   (.then (fn [x] (.text x)))
   (.then (fn [res]
            (let [exp_path (.replace path ".xml" ".json")]
              (->
               (fs/readFile exp_path "utf-8")
               (.then (fn [expected]
                        (println "Test result:" (= expected res) "|" path)
                        (if (= expected res) nil
                            (do
                              (println res)
                              (.exit process 1)))))
               (.catch (fn [] (fs/writeFile exp_path res)))))))))

(->
 (w/unstable_dev "bin/test/main_test.js")
 (.then (fn [worker]
          (->
           (Promise.resolve nil)
           (.then (fn [] (assert worker "/test/"  "../test/xml_parser/androidx-release-notes.xml")))
           (.then (fn [] (assert worker "/test2/" "../test/xml_parser/izpodshtorki.xml")))
           (.then (fn [] (assert worker "/test2/" "../test/xml_parser/razborfeed.xml")))
           (.then (fn [] (.stop worker)))))))
