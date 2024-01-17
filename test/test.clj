(import [fs.promises :as fs])
(require [vendor.effects :as e]
         [main :as app])

(defn- assert [path]
  (->
   (fs/readFile path "utf-8")
   (.then
    (fn [log_json]
      (let [log (.reverse (JSON/parse log_json))]
        (defn- attach_test_effect [world name]
          (e/attach_eff
           world name
           (fn [args]
             (let [x (.pop log)]
               (if (= (JSON/stringify x.in) (JSON/stringify [name args]))
                 (Promise/resolve x.out)
                 (FIXME "Log: " path "\n" (JSON/stringify x.in) "\n!=\n" (JSON/stringify [name args]) "\n"))))))
        (->
         (app/handle (let [x (.pop log)] x.out))
         (e/run_effect (-> {} (attach_test_effect :fetch) (attach_test_effect :db)))
         (.then (fn []
                  (if (= log.length 0) null
                      (FIXME "Log not consumed: " path "\n" (JSON/stringify (.toReversed log) null 2)))))))))))

(let [path "../test/samples/"]
  (->
   (fs/readdir path)
   (.then
    (fn [files]
      (.map files (fn [name] (assert (str path name))))))))
