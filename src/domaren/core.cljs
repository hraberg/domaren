(ns ^:figwheel-always domaren.core
    (:require [clojure.string :as s]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!" :count 2}))
(defonce force-rerender (atom false))

;; From https://github.com/weavejester/hiccup
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(declare render-component-fn!)

(defn node-attributes [dom-node]
  (let [attributes (.-attributes dom-node)]
    (areduce attributes idx acc []
             (conj acc (.-name (.item attributes idx))))))

(defn hiccup->dom! [dom-node hiccup]
  (cond
    (and (vector? hiccup)
         (fn? (first hiccup)))
    (apply render-component-fn! dom-node hiccup)

    (vector? hiccup)
    (let [[tag & [attributes :as children]] hiccup
          [attributes children] (if (map? attributes)
                                  [attributes (rest children)]
                                  [{} children])
          [_ tag id class] (re-find re-tag (name tag))
          classes (s/split class ".")
          element (if (= (s/upper-case tag) (some-> dom-node .-tagName))
                    dom-node
                    (let [element (.createElement js/document tag)]
                      (set! (.-__domaren element) #js {})
                      element))
          key-map (or (-> element .-__domaren .-keys) #js {})
          new-key-map #js {}]
      (when-not (= (-> element .-__domaren .-hiccup) hiccup)
        (doseq [[k v] {"id" id "className" (s/join " " classes)}
                :when (not= (aget element k) v)]
          (aset element k (or v "")))
        (doseq [[k v] attributes
                :let [k (name k)]
                :when (not= (.getAttribute element k) v)]
          (.setAttribute element k (or v "")))
        (doseq [k (remove (into #{"id" "className"} (map name (keys attributes)))
                          (node-attributes element))]
          (.removeAttribute element k))
        (loop [[h & hs] children child (.-firstChild element)]
          (cond
            (seq? h)
            (recur (concat h hs) child)

            h
            (let [key (some-> h meta :key str)
                  old-child (aget key-map key)
                  child (if (and child old-child
                                 (not= child old-child))
                          (do
                            (.replaceChild element old-child child)
                            old-child)
                          child)
                  new-child (hiccup->dom! child h)]
              (when key
                (aset new-key-map key new-child))
              (cond
                (and child (not= child new-child))
                (.replaceChild element new-child child)

                (not child)
                (.appendChild element new-child))
              (recur hs (.-nextSibling new-child)))

            :else
            (when-let [last-child (some-> child .-previousSibling)]
              (while (.-nextSibling last-child)
                (.remove (.-nextSibling last-child))))))
        (set! (.-keys (.-__domaren element)) new-key-map)
        (set! (.-hiccup (.-__domaren element)) hiccup))
      element)

    :else
    (if (and dom-node (= (.-TEXT_NODE js/Node)
                         (.-nodeType dom-node)))
      (do
        (when-not (= (.-textContent dom-node) hiccup)
          (set! (.-textContent dom-node) hiccup))
        dom-node)
      (.createTextNode js/document hiccup))))

(defn should-component-update? [dom-node state]
  (or (not= (some-> dom-node .-__domaren .-state) state)
      @force-rerender))

(defn render-component-fn! [dom-node f & state]
  (let [state (vec state)]
    (if (should-component-update? dom-node state)
      (time
       (try
         (println "Rendering Component" (.-name f) dom-node state)
         (let [new-node (hiccup->dom! dom-node (apply f state))]
           (set! (.-state (.-__domaren new-node)) state)
           new-node)))
      dom-node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn render-loop! [f dom-node state]
  (js/requestAnimationFrame
   (fn tick []
     (try
       (let [current-node (.-firstChild dom-node)
             new-node (render-component-fn! current-node (maybe-deref f) (maybe-deref state))]
         (when-not (= new-node current-node)
           (set! (.-innerHTML dom-node) "")
           (.appendChild dom-node new-node)))
       (finally
         (reset! force-rerender false)
         (js/requestAnimationFrame tick))))))

(defn foo-component [count]
  [:pre count])

(defn render-app [state]
  [:div#2.foo.bar {:title "FOO"}
   [:h1 (:text state)]
   (for [i (shuffle (range 3))]
     (with-meta [:span i] {:key i}))
   [foo-component (* 5 (:count state))]
   [foo-component (* 5 (:count state))]
   [foo-component (* 2 (:count state))]
   [foo-component (* 2 (:count state))]])

(render-loop! #'render-app
              (.getElementById js/document "app")
              app-state)

(defn on-js-reload []
  (println "Forcing rerender")
  (reset! force-rerender true))

(comment
  (do
    (require 'figwheel-sidecar.repl-api)
    (figwheel-sidecar.repl-api/cljs-repl)))
