(ns ^:figwheel-always domaren.core
    (:require [clojure.string :as s]))

(def DEBUG false)
(def TIME false)

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
          :let [k (name k)]
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

(defn event-handlers [attributes]
  (into {} (filter (comp fn? val) attributes)))

;; From https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn html->dom! [node hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (rest children)]
                                [{} children])
        [_ tag id class] (re-find re-tag (name tag))
        class (->> (:class attributes)
                   (concat (s/split class "."))
                   (s/join " "))
        attributes (merge attributes {:id id :class class})
        handlers (event-handlers attributes)
        attributes (apply dissoc attributes (keys handlers))
        node (create-element node tag)
        key-map (-> node .-__domaren .-keys)
        new-key-map #js {}]
    (when (markup-changed? node hiccup)
      (doto node
        (add-properties! handlers)
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
    (apply component->dom! node (meta hiccup) hiccup)

    (hiccup? hiccup)
    (html->dom! node hiccup)

    :else
    (text->dom! node (str hiccup))))

(defn component-name [f]
  (s/replace (.-name f) "$" "."))

(defonce request-refresh (atom false))
(def ^:dynamic *refresh* false)

(defn should-component-update? [node state]
  (or (not= (some-> node .-__domaren .-state) state)
      *refresh*))

(defn component-will-mount? [node]
  (not (.-parentNode node)))

(def ^:dynamic *mounted-nodes*)

(defn component-callbacks! [node {:keys [did-mount will-mount]}]
  (when (component-will-mount? node)
    (when will-mount
      (will-mount node))
    (when did-mount
      (swap! *mounted-nodes* conj (partial did-mount node)))))

(defn component->dom! [node opts f & state]
  (let [state (vec state)
        component-name (component-name f)]
    (if (should-component-update? node state)
      (try
        (when TIME
          (.time js/console component-name))
        (when DEBUG
          (.debug js/console component-name node (s/trim (pr-str state))))
        (doto (hiccup->dom! node (apply f state))
          (aset "__domaren" "state" state)
          (component-callbacks! opts))
        (finally
          (when TIME
            (.timeEnd js/console component-name))))
      node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn render! [node f state]
  (js/requestAnimationFrame
   (fn tick []
     (binding [*refresh* (compare-and-set! request-refresh true false)
               *mounted-nodes* (atom [])]
       (try
         (let [current-node (.-firstChild node)
               new-node (component->dom! current-node {} (maybe-deref f) (maybe-deref state))]
           (when-not (= new-node current-node)
             (doto node
               (aset "innerHTML" "")
               (.appendChild new-node))))
         (finally
           (doseq [f @*mounted-nodes*]
             (f))
           (js/requestAnimationFrame tick)))))))

(defn refresh! []
  (.info js/console "refresh!")
  (reset! request-refresh true))
