(ns ^:figwheel-always domaren.dev
    (:require [domaren.core]))

(enable-console-print!)

(set! domaren.core/DEBUG true)
(set! domaren.core/TIME true)

(defonce app-state (atom {:text "Hello world!" :count 2}))

(defn foo-component [count f]
  [:pre {:onclick (fn [evt] (when f (f evt)))} count])

(defn render-app [state]
  [:div#2.foo.bar {:title "FOO"}
   [:h1 (:text state)]
   (for [i (shuffle (range 3))]
     ^{:key i} [:span i])
   ^{:will-mount (fn [node count]
                   (.debug js/console "Will Mount" node (.-parentNode node) (pr-str count)))}
   [foo-component (* 5 (:count state))]
   [foo-component (* 5 (:count state))]
   [foo-component (* 2 (:count state))]
   [foo-component (* 2 (:count state)) (fn [evt] (js/alert "!"))]
   ^{:did-mount (fn [node count]
                  (.debug js/console "Did Mount" node (.-parentNode node)  (pr-str count)))}
   [foo-component (:count state)]])

(domaren.core/render!
 (.getElementById js/document "app")
 #'render-app
 app-state)

(comment
  (do
    (require 'figwheel-sidecar.repl-api)
    (figwheel-sidecar.repl-api/cljs-repl))

  (reset! app-state {:text "Hello", :count 3}))
