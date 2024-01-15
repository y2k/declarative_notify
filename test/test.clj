(__unsafe_insert_js "import { promises as fs } from 'fs';")
(__unsafe_insert_js "import * as e from './vendor/effects.js';")
(__unsafe_insert_js "import * as app from './main.js';")

(defn- assert [name]
  (->
   (fs/readFile (str "../test/samples/" name) "utf-8")
   (.then
    (fn [log_json]
      (let [log (.reverse (JSON/parse log_json))]
        (e/run_effect
         (app/handle (let [x (.pop log)] x.out))
         {:perform
          (fn [name args]
            (let [x (.pop log)]
              (if (= (JSON/stringify x.in) (JSON/stringify [name args]))
                (Promise/resolve x.out)
                (FIXME "\n" (JSON/stringify x.in) "\n!=\n" (JSON/stringify [name args]) "\n"))))}))))))

(assert "log1.json")
(assert "log2.json")
(assert "log3.json")
