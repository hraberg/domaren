(ns vdom-benchmark-domaren.core
  (:require [domaren.core :as d])
  (:refer-clojure :exclude [update]))

(defn render-tree [nodes]
  (for [n nodes
        :let [k (.-key n)]]
    (if (.-children n)
      ^{:key k} [:div (render-tree (.-children n))]
      ^{:key k} [:span (str k)])))

(defrecord BenchmarkImpl [container a b]
  Object
  (setUp [_])

  (tearDown [_]
    (d/remove-root! container))

  (render [_]
    (d/render-root! container [:div (render-tree a)]))

  (update [_]
    (d/render-root! container [:div (render-tree b)])))

(aset js/module "exports" BenchmarkImpl)
