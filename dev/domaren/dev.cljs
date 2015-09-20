(ns ^:figwheel-always domaren.dev
    (:require [domaren.core]))

(enable-console-print!)

(set! domaren.core/DEBUG true)
(set! domaren.core/TIME true)

(defonce app-state (atom {:text "Hello world!" :count 2}))

(defn foo-component [count]
  [:pre count])

(defn render-app [state]
  [:div#2.foo.bar {:title "FOO"}
   [:h1 (:text state)]
   (for [i (shuffle (range 3))]
     ^{:key i} [:span i])
   [foo-component (* 5 (:count state))]
   [foo-component (* 5 (:count state))]
   [foo-component (* 2 (:count state))]
   [foo-component (* 2 (:count state))]
   [foo-component (:count state)]])

(domaren.core/render-loop!
 #'render-app
 (.getElementById js/document "app")
 app-state)

(comment
  (do
    (require 'figwheel-sidecar.repl-api)
    (figwheel-sidecar.repl-api/cljs-repl))

  (reset! app-state {:text "Hello", :count 3}))
