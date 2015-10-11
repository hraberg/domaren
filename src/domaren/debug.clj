(ns domaren.debug)

(def DEBUG false)

(defmacro debug [& args]
  (when DEBUG
    `(.apply (.-debug js/console) js/console (into-array (map #(if ((some-fn keyword? coll?) %)
                                                                 (pr-str %)
                                                                 %) ~args)))))
