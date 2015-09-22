(ns ^:figwheel-always domaren.core
    (:require [clojure.string :as s]))

(def DEBUG false)
(def TIME_COMPONENTS false)
(def TIME_FRAME false)

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

(defn keep-attributes! [node keep-attribute?]
  (doseq [k (node-attributes node)
          :when (not (keep-attribute? k))]
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

(defn align-children! [node children]
  (let [key-map (aget node "__domaren" "keys")
        new-key-map #js {}]
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
    (aset node "__domaren" "keys" new-key-map)))

(defn html->dom! [node hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (rest children)]
                                [{} children])
        [_ tag id class] (re-find re-tag (name tag))
        class (->> (:class attributes)
                   (conj (s/split class #"\."))
                   (s/join " "))
        id (attributes :id id)
        handlers (event-handlers attributes)
        properties (select-keys attributes [:value :type :checked])
        attributes (merge (apply dissoc attributes (keys handlers))
                          {:id id :class class})]
    (doto (create-element node tag)
      (add-properties! (merge properties handlers))
      (add-attributes! attributes)
      (keep-attributes! (set (map name (keys attributes))))
      (align-children! children))))

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

(def re-component-name #"\$")

(defn component-name [f]
  (s/replace (or (.-name f) "<anonymous>") re-component-name "."))

(defonce request-refresh (atom false))
(def ^:dynamic *refresh* false)

(defn component-state [node]
  (some-> node .-__domaren .-state))

(defn should-component-update? [node state]
  (or (not= (component-state node) state)
      *refresh*))

(defn component-will-mount? [node]
  (not (.-parentNode node)))

(def ^:dynamic *mounted-nodes*)

;; See https://facebook.github.io/react/docs/component-specs.html
;; These callbacks are a subset and don't work exactly the same way.
(defn component-callbacks! [node {:keys [did-mount will-mount did-update]} previous-state state]
  (if (component-will-mount? node)
    (do
      (some-> will-mount (apply node state))
      (when did-mount
        (swap! *mounted-nodes* conj (fn [] (apply did-mount node state)))))
    (some-> did-update (apply node previous-state state))))

(defn component->dom! [node opts f & state]
  (let [state (vec state)]
    (if (should-component-update? node state)
      (let [component-name (component-name f)
            time? (or TIME_COMPONENTS (and (:root opts) TIME_FRAME))
            opts (merge (meta f) opts)]
        (try
          (when time?
            (.time js/console component-name))
          (when DEBUG
            (.debug js/console component-name node (s/trim (pr-str state))))
          (doto (hiccup->dom! node (apply f state))
            (component-callbacks! opts (component-state node) state)
            (aset "__domaren" "state" state))
          (finally
            (when time?
              (.timeEnd js/console component-name)))))
      node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn render! [node f & state]
  (js/requestAnimationFrame
   (fn tick []
     (binding [*refresh* (compare-and-set! request-refresh true false)
               *mounted-nodes* (atom [])]
       (try
         (let [current-node (.-firstChild node)
               new-node (apply component->dom! current-node {:root true} (maybe-deref f) (map maybe-deref state))]
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
