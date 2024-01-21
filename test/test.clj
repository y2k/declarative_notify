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
        (fn [cofx_log_json]
          (let [cofx_log1 (JSON/parse cofx_log_json)
                fx_log (JSON/parse log_json)
                log (.reverse (.concat cofx_log1 fx_log))]
            (defn- attach_test_effect [world name]
              (e/attach_eff
               world name
               (fn [args]
                 (if (> log.length 0)
                   (let [x (.pop log)]
                     (if (= (JSON/stringify x.in) (JSON/stringify [name args]))
                       (Promise/resolve x.out)
                       (FIXME "Log: " path "\n" (JSON/stringify x.in) "\n!=\n" (JSON/stringify [name args]) "\n")))
                   (do
                     (.push fx_log {:in [name args] :out (if (= :db name) [] {})})
                     (Promise/resolve {}))))))
            (->
             (app/handle (let [x (.pop log)] x.out))
             (e/run_effect (-> {} (attach_test_effect :fetch) (attach_test_effect :db)))
             (.then (fn []
                      (if (= (JSON/parse log_json) 0)
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
       (.filter (fn [name] (.test (RegExp. "^cofx.log\\d+\\.json$") name)))
       (.map (fn [name] (assert (str path name)))))))))
