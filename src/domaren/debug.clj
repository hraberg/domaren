(ns domaren.debug)

(def DEBUG false)

(defmacro debug [& args]
  (when DEBUG
    `(.apply (.-debug js/console) js/console
             (into-array (map #(cond-> %
                                 ((some-fn coll? keyword?) %) pr-str)
                              (remove #(cljs.core/undefined? %) ~(vec args)))))))
