(import [fs.promises :as fs])
(require [vendor.effects :as e]
         [main :as app])

(defn- assert [path]
  (->
   (fs/readFile (.replace path "cofx." "") "utf-8")
   (.catch (fn [] "[]"))
   (.then
    (fn [log_json]
      (->
       (fs/readFile path "utf-8")
       (.then
        (fn [event_json]
          (let [event (JSON/parse event_json)
                fx_log []
                log (.reverse (JSON/parse log_json))]
            (defn- attach_test_effect [world name]
              (e/attach_eff
               world name
               (fn [args]
                ;;  (println "LOG, args: " args)
                 (if (> log.length 0)
                   (let [x (.pop log)]
                    ;;  (println "FIXME2\n" (JSON/stringify [x.key x.data]) "\n" (JSON/stringify [name args]))
                     (if (= (JSON/stringify [x.key x.data]) (JSON/stringify [name args]))
                       (Promise/resolve x.out)
                       (FIXME "Log: " (.replace path "cofx." "") "\n" (JSON/stringify [x.key x.data]) "\n!=\n" (JSON/stringify [name args]) "\n")))
                   (do
                     (.push fx_log {:key name :data args})
                     (Promise/resolve null))))))
            (->
             (app/handle_event event.key event.data)
             (e/run_effect (-> {} (attach_test_effect :dispatch) (attach_test_effect :fetch) (attach_test_effect :db)))
             (.then (fn []
                      (if (= log_json "[]")
                        (fs/writeFile (.replace path "cofx." "") (JSON/stringify fx_log null 4))
                        (if (= log.length 0) null
                            (FIXME "Log not consumed: " path "\n" (JSON/stringify (.toReversed log) null 2)))))))))))))))

(let [path "../test/samples/"]
  (->
   (fs/readdir path)
   (.then
    (fn [files]
      (->
       files
       (.filter (fn [name] (.test (RegExp. "^cofx.log[\\d_]+\\.json$") name)))
       (.map (fn [name] (assert (str path name)))))))))
