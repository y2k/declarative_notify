(defn run_effect [fx a] (fx a))

(defn pure [x]
  (fn [env] (Promise/resolve x)))

(defn batch [xs]
  (fn [env] (->
             (.map xs (fn [f] (f env)))
             (Promise/all))))

(defn then [fx f]
  (fn [env]
    (let [pr (fx env)]
      (.then
       pr
       (fn [r] (let [r2 (f r)]
                 (r2 env)))))))

(defn attach_eff [world key eff]
  (assoc
   world :perform
   (fn [name args]
     (if (not= name key)
       (world/perform name args)
       (eff args)))))
