(ns ^:figwheel-always domaren.core
    (:require [clojure.string :as s]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!" :count 2}))
(defonce force-rerender (atom false))

;; From https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(declare component->dom! hiccup->dom!)

(defn node-attributes [node]
  (let [attributes (.-attributes node)]
    (areduce attributes idx acc []
             (conj acc (.-name (.item attributes idx))))))

(defn hiccup? [x]
  (and (vector? x) (keyword? (first x))))

(defn component? [x]
  (and (vector? x) (fn? (first x))))

(defn create-element [node tag]
  (if (= (s/upper-case tag) (some-> node .-tagName))
    node
    (doto (.createElement js/document tag)
      (aset "__domaren" #js {}))))

(defn markup-changed? [node hiccup]
  (not= (-> node .-__domaren .-hiccup) hiccup))

(defn add-properties! [node properties]
  (doseq [[k v] properties
          :when (not= (aget node k) v)]
    (aset node k (or v ""))))

(defn add-attributes! [node attributes]
  (doseq [[k v] attributes
          :let [k (name k)]
          :when (not= (.getAttribute node k) v)]
    (.setAttribute node k (or v ""))))

(defn remove-attributes! [node attributes]
  (doseq [k attributes]
    (.removeAttribute node k)))

(defn remove-all-children-after! [node]
  (while (some-> node .-nextSibling)
    (.remove (.-nextSibling node))))

(defn html->dom! [node hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (rest children)]
                                [{} children])
        [_ tag id class] (re-find re-tag (name tag))
        classes (s/split class ".")
        node (create-element node tag)
        key-map (-> node .-__domaren .-keys)
        new-key-map #js {}
        properties {"id" (or id (:id attributes))
                    "className" (s/join " " (concat classes (:class attributes)))}]
    (when (markup-changed? node hiccup)
      (doto node
        (add-properties! properties)
        (add-attributes! attributes)
        (remove-attributes! (remove (set (map name (keys attributes)))
                                    (node-attributes node))))
      (loop [[h & hs] children child (.-firstChild node)]
        (cond
          (seq? h)
          (recur (concat h hs) child)

          h
          (let [key (some-> h meta :key str)
                old-child (some-> key-map (aget key))
                child (if (and child old-child
                               (not= child old-child))
                        (do
                          (.insertBefore node old-child child)
                          old-child)
                        child)
                new-child (hiccup->dom! child h)]
            (when key
              (aset new-key-map key new-child))

            (cond
              (and child (not= child new-child))
              (.replaceChild node new-child child)

              (not child)
              (.appendChild node new-child))
            (recur hs (.-nextSibling new-child)))

          :else
          (remove-all-children-after! (some-> child .-previousSibling))))
      (doto (.-__domaren node)
        (aset "keys" new-key-map)
        (aset "hiccup" hiccup)))
    node))

(defn text-node? [node]
  (some-> node .-nodeType (== (.-TEXT_NODE js/Node))))

(defn text->dom! [node text]
  (if (text-node? node)
    (cond-> node
      (not= (.-textContent node) text) (doto (aset "textContent" text)))
    (.createTextNode js/document text)))

(defn hiccup->dom! [node hiccup]
  (cond
    (component? hiccup)
    (apply component->dom! node hiccup)

    (hiccup? hiccup)
    (html->dom! node hiccup)

    :else
    (text->dom! node (str hiccup))))

(defn should-component-update? [node state]
  (or (not= (some-> node .-__domaren .-state) state)
      @force-rerender))

(defn component->dom! [node f & state]
  (let [state (vec state)]
    (if (should-component-update? node state)
      (do
        (.log js/console "Rendering Component"
              (s/replace (.-name f) "$" ".") node (pr-str state))
        (time
         (doto (hiccup->dom! node (apply f state))
           (aset "__domaren" "state" state))))
      node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn render-loop! [f node state]
  (js/requestAnimationFrame
   (fn tick []
     (try
       (let [current-node (.-firstChild node)
             new-node (component->dom! current-node (maybe-deref f) (maybe-deref state))]
         (when-not (= new-node current-node)
           (doto node
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
   [foo-component (* 2 (:count state))]
   [foo-component (:count state)]])

(render-loop! #'render-app
              (.getElementById js/document "app")
              app-state)

(defn on-js-reload []
  (.log js/console "Forcing rerender")
  (reset! force-rerender true))

(comment
  (do
    (require 'figwheel-sidecar.repl-api)
    (figwheel-sidecar.repl-api/cljs-repl)))
