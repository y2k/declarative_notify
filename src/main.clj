(ns app (:require ["../vendor/packages/effects/effects.2" :as e]
                  [core :as c]))

(export-default
 {:fetch
  (fn [request env ctx]
    (->
     (.json request)
     (.then (fn [update]
              (let [world (->
                           env
                           e/attach_empty_effect_handler
                           (e/attach_eff :database
                                         (fn [_ {sql :sql sql_args :args}]
                                           (->
                                            env.DB (.prepare sql) (.bind (spread sql_args)) .run
                                            (.then (fn [x] x.results)))))
                           (e/attach_eff :fetch
                                         (fn [_ {url :url props :props}]
                                           (->
                                            (.replaceAll url "~TG_TOKEN~" env.TG_TOKEN)
                                            (fetch props))))
                           (e/attach_eff :dispatch
                                         (fn [world [key data]]
                                           (e/run_effect (c/handle_event key data) world)))
                           e/attach_log)]
                (e/run_effect (c/handle_event :telegram update) world))))
     (.catch console.error)
     (.then (fn [] (Response. (str "OK - " (Date.)))))))})
