(ns domaren.core
    (:require [clojure.string :as s]))

(def DEBUG false)
(def TIME_COMPONENTS false)
(def TIME_FRAME false)

(declare component->dom! hiccup->dom!)

(defn node-attributes [node]
  (.keys js/Object (aget node "__domaren" "attrs")))

(defn hiccup? [x]
  (and (vector? x) (keyword? (first x))))

(defn component? [x]
  (and (vector? x) (fn? (first x))))

(def elements #js {})

(defn create-element [node tag]
  (if (= tag (some-> node .-__domaren .-tag))
    node
    (doto (-> (or (aget elements tag)
                  (aset elements tag (.createElement js/document tag)))
              (.cloneNode false))
      (aset "__domaren" #js {:tag tag :attrs #js {}}))))

(defn set-properties! [node properties]
  (doseq [[k v] properties
          :let [k (name k)]
          :when (not= (aget node k) v)]
    (aset node k v)))

(defn add-attributes! [node attributes]
  (doseq [:let [attrs (aget node "__domaren" "attrs")]
          [k v] attributes
          :let [k (name k)]
          :when (not= (aget attrs k) v)]
    (if-let [v (aset attrs k v)]
      (.setAttribute node k v)
      (.removeAttribute node k))))

(defn keep-attributes! [node keep-attribute?]
  (doseq [:let [attrs (aget node "__domaren" "attrs")]
          k (node-attributes node)
          :when (not (keep-attribute? k))]
    (js-delete attrs k)
    (.removeAttribute node k)))

(defn remove-children-starting-at! [node]
  (when node
    (while (.-nextSibling node)
      (.remove (.-nextSibling node)))
    (.remove node)))

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
                      (.insertBefore node old-child child)
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
        (remove-children-starting-at! child)))
    (aset node "__domaren" "keys" new-key-map)))

(def re-class #"\.")

(def normalize-tag
  (memoize
   (fn normalize-tag [tag attributes]
     (let [[_ tag id class] (re-find re-tag (name tag))
           class (->> (:class attributes)
                      (conj (s/split class re-class))
                      (s/join " ")
                      s/trim)
           id (attributes :id id)
           handlers (event-handlers attributes)
           form-properties (select-keys attributes [:value :checked :selected :selectedIndex])
           properties (cond-> (merge form-properties handlers)
                        class (assoc :className class))
           attributes (cond-> (apply dissoc attributes :id :class (keys properties))
                        id (assoc :id id))]
       {:tag tag :attributes attributes :properties properties
        :attributes-to-keep (set (mapv name (keys attributes)))}))))

(defn html->dom! [node hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (next children)]
                                [{} children])
        {:keys [tag attributes attributes-to-keep properties]} (normalize-tag tag attributes)]
    (doto (create-element node tag)
      (add-attributes! attributes)
      (keep-attributes! attributes-to-keep)
      (set-properties! properties)
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

(defonce request-refresh (atom false))
(def ^:dynamic *refresh* false)

(def re-component-name #"\$")

(defn component-name [f]
  (s/replace (or (.-name f) "<anonymous>") re-component-name "."))

(defn component-fn [node]
  (some-> node .-__domaren .-component))

(defn component-state [node]
  (some-> node .-__domaren .-state))

(defn should-component-update? [node state]
  (or (not (and node (= (component-state node) state)))
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
  (let [node (when (= f (component-fn node))
               node)
        state (vec state)]
    (if (should-component-update? node state)
      (let [time? (or TIME_COMPONENTS (and (:root opts) TIME_FRAME))
            opts (merge (meta f) opts)
            component-name (component-name f)]
        (try
          (when time?
            (.time js/console component-name))
          (when DEBUG
            (.debug js/console component-name node (s/trim (pr-str state))))
          (doto (hiccup->dom! node (apply f state))
            (component-callbacks! opts (component-state node) state)
            (aset "__domaren" "component" f)
            (aset "__domaren" "state" state))
          (finally
            (when time?
              (.timeEnd js/console component-name)))))
      node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

;; Public API

(defn render-root!
  "Passes all state to f which should return a Hiccup-style tree,
  which is added or reconciled below the provided DOM node as a single
  child."
  [node f & state]
  (let [current-node (.-firstChild node)
        new-node (apply component->dom! current-node {:root true} f state)]
    (when-not (= new-node current-node)
      (doto node
        (aset "innerHTML" "")
        (.appendChild new-node)))))

(defn render!
  "Render loop using requestAnimationFrame and IWatch to track state
  changes, usually on atoms. Derefences both f and state before
  passing them to render-root! on state change."
  [node f & state]
  (let [f (maybe-deref f)
        tick-requested? (atom false)
        tick #(binding [*refresh* (compare-and-set! request-refresh true false)
                        *mounted-nodes* (atom [])]
                (reset! tick-requested? false)
                (try
                  (apply render-root! node f (mapv maybe-deref state))
                  (finally
                    (doseq [f @*mounted-nodes*]
                      (f)))))
        request-tick! #(when-not @tick-requested?
                         (reset! tick-requested? true)
                         (js/requestAnimationFrame tick))]
    (doseq [w (filter #(satisfies? IWatchable %) state)]
      (-add-watch w ::tick request-tick!))
    (request-tick!)))

(defn refresh!
  "Forces a full refersh of the DOM."
  []
  (.info js/console "refresh!")
  (reset! request-refresh true))
