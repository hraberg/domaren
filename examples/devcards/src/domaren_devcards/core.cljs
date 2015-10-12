(ns domaren-devcards.core
  (:require [domaren.core :as d])
  (:require-macros [devcards.core :as dc :refer [defcard dom-node]]))

(enable-console-print!)

(defonce state (atom {:count 0}))

(defcard increment
  (dom-node
   (fn [data-atom node]
     (d/render-root!
      node
      [:button
       {:onclick #(swap! data-atom update-in [:count] inc)}
       "increment"])))
  state
  {:heading false})

(defcard counter
  (dom-node
   (fn [data-atom node]
     (d/render! node
                (fn [state]
                  [:h1 "Count: " (:count state)])
                data-atom)))
  state
  {:inspect-data true :history true})
