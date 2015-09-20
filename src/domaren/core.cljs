(ns ^:figwheel-always domaren.core
    (:require [clojure.string :as s]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!" :count 2}))
(defonce force-rerender (atom false))

;; From https://github.com/weavejester/hiccup
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(declare component->dom! hiccup->dom!)

(defn node-attributes [dom-node]
  (let [attributes (.-attributes dom-node)]
    (areduce attributes idx acc []
             (conj acc (.-name (.item attributes idx))))))

(defn hiccup? [x]
  (and (vector? x) (keyword? (first x))))

(defn component? [x]
  (and (vector? x) (fn? (first x))))

(defn create-element [dom-node tag]
  (if (= (s/upper-case tag) (some-> dom-node .-tagName))
    dom-node
    (doto (.createElement js/document tag)
      (aset "__domaren" #js {}))))

(defn markup-changed? [dom-node hiccup]
  (not= (-> dom-node .-__domaren .-hiccup) hiccup))

(defn add-properties! [dom-node properties]
  (doseq [[k v] properties
          :when (not= (aget dom-node k) v)]
    (aset dom-node k (or v ""))))

(defn add-attributes! [dom-node attributes]
  (doseq [[k v] attributes
          :let [k (name k)]
          :when (not= (.getAttribute dom-node k) v)]
    (.setAttribute dom-node k (or v ""))))

(defn remove-attributes! [dom-node attributes]
  (doseq [k attributes]
    (.removeAttribute dom-node k)))

(defn remove-all-children-after! [dom-node]
  (when dom-node
    (while (.-nextSibling dom-node)
      (.remove (.-nextSibling dom-node)))))

(defn html->dom! [dom-node hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (rest children)]
                                [{} children])
        [_ tag id class] (re-find re-tag (name tag))
        classes (s/split class ".")
        element (create-element dom-node tag)
        key-map (-> element .-__domaren .-keys)
        new-key-map #js {}
        properties {"id" (or id (:id attributes))
                    "className" (s/join " " (concat classes (:class attributes)))}]
    (when (markup-changed? element hiccup)
      (doto element
        (add-properties! properties)
        (add-attributes! attributes)
        (remove-attributes! (->> element
                                 node-attributes
                                 (remove (set (map name (keys attributes)))))))
      (loop [[h & hs] children child (.-firstChild element)]
        (cond
          (seq? h)
          (recur (concat h hs) child)

          h
          (let [key (some-> h meta :key str)
                old-child (some-> key-map (aget key))
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
          (remove-all-children-after! (some-> child .-previousSibling))))
      (doto (.-__domaren element)
        (aset "keys" new-key-map)
        (aset "hiccup" hiccup)))
    element))

(defn text->dom! [dom-node text]
  (if (and dom-node (= (.-TEXT_NODE js/Node)
                       (.-nodeType dom-node)))
    (cond-> dom-node
      (not= (.-textContent dom-node) text) (doto (aset "textContent" text)))
    (.createTextNode js/document text)))

(defn hiccup->dom! [dom-node hiccup]
  (cond
    (component? hiccup)
    (apply component->dom! dom-node hiccup)

    (hiccup? hiccup)
    (html->dom! dom-node hiccup)

    :else
    (text->dom! dom-node (str hiccup))))

(defn should-component-update? [dom-node state]
  (or (not= (some-> dom-node .-__domaren .-state) state)
      @force-rerender))

(defn component->dom! [dom-node f & state]
  (let [state (vec state)]
    (if (should-component-update? dom-node state)
      (do
        (println "Rendering Component" (.-name f) dom-node state)
        (time
         (doto (hiccup->dom! dom-node (apply f state))
           (aset "__domaren" "state" state))))
      dom-node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn render-loop! [f dom-node state]
  (js/requestAnimationFrame
   (fn tick []
     (try
       (let [current-node (.-firstChild dom-node)
             new-node (component->dom! current-node (maybe-deref f) (maybe-deref state))]
         (when-not (= new-node current-node)
           (doto dom-node
             (aset "innerHTML" "")
             (.appendChild new-node))))
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
