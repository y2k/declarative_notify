(__unsafe_insert_js "import { promises as fs } from 'fs';")
(__unsafe_insert_js "import * as e from './vendor/effects.js';")
(__unsafe_insert_js "import * as app from './main.js';")

(defn- assert [path]
  (->
   (fs/readFile path "utf-8")
   (.then
    (fn [log_json]
      (let [log (.reverse (JSON/parse log_json))]
        (->
         (e/run_effect
          (app/handle (let [x (.pop log)] x.out))
          {:perform
           (fn [name args]
             (let [x (.pop log)]
               (if (= (JSON/stringify x.in) (JSON/stringify [name args]))
                 (Promise/resolve x.out)
                 (FIXME "Log: " path "\n" (JSON/stringify x.in) "\n!=\n" (JSON/stringify [name args]) "\n"))))})
         (.then (fn []
                  (if (= log.length 0) null
                      (FIXME "Log not consumed: " path "\n" (JSON/stringify (.toReversed log) null 2)))))))))))

(let [path "../test/samples/"]
  (->
   (fs/readdir path)
   (.then
    (fn [files]
      (.map files (fn [name] (assert (str path name))))))))
