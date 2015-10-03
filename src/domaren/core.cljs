(ns domaren.core
    (:require [clojure.string :as s]))

(def DEBUG false)
(def TIME_COMPONENTS false)
(def TIME_FRAME false)

(declare component->dom! hiccup->dom! hiccup->str)

(defn node-state
  ([node]
   (some-> node (aget "__domaren")))
  ([node k]
   (some-> (node-state node) (aget k))))

(defn set-node-state! [node k v]
  (aset (node-state node) k v))

(defn init-node-state! [node]
  (let [tag (s/lower-case (.-tagName node))]
    (aset node "__domaren" #js {:tag tag :attrs #js {}})))

(defn hiccup? [x]
  (and (vector? x) (keyword? (first x))))

(defn component? [x]
  (and (vector? x) (fn? (first x))))

(def elements #js {})

(defn create-element [node tag]
  (if (= tag (node-state node "tag"))
    node
    (doto (-> (or (aget elements tag)
                  (aset elements tag (.createElement js/document tag)))
              (.cloneNode false))
      init-node-state!)))

(defn set-properties! [node properties]
  (doseq [[k v] properties
          :let [k (name k)]
          :when (not= (aget node k) v)]
    (aset node k v)))

(defn add-attributes! [node attributes]
  (doseq [:let [attrs (node-state node "attrs")]
          [k v] attributes
          :let [k (name k)]
          :when (not= (aget attrs k) v)]
    (if-let [v (aset attrs k v)]
      (.setAttribute node k v)
      (.removeAttribute node k))))

(defn keep-attributes! [node keep-attribute?]
  (doseq [:let [attrs (node-state node "attrs")]
          k (.keys js/Object attrs)
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
  (let [key-map (node-state node "keys")
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
    (set-node-state! node "keys" new-key-map)))

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
           properties (cond-> form-properties
                        class (assoc :className class))
           attributes (cond-> (apply dissoc attributes :id :class
                                     (concat (keys handlers) (keys properties)))
                        id (assoc :id id))]
       {:tag tag :attributes attributes :properties properties :handlers handlers
        :attributes-to-keep (set (mapv name (keys attributes)))}))))

(defn normalize-hiccup [hiccup]
  (let [[tag & [attributes :as children]] hiccup
        [attributes children] (if (map? attributes)
                                [attributes (next children)]
                                [{} children])]
    (assoc (normalize-tag tag attributes) :children children)))

(defn register-event-handlers! [node handlers]
  (doseq [[k v] handlers]
    (set-node-state! node (name k) v)))

(defn html->dom! [node hiccup]
  (let [{:keys [tag attributes attributes-to-keep handlers
                properties children]} (normalize-hiccup hiccup)]
    (doto (create-element node tag)
      (add-attributes! attributes)
      (keep-attributes! attributes-to-keep)
      (set-properties! properties)
      (align-children! children)
      (register-event-handlers! handlers))))

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

(defn html->str [hiccup]
  (let [{:keys [tag attributes
                properties children]} (normalize-hiccup hiccup)
        attributes (-> attributes
                       (merge properties)
                       (dissoc :className)
                       (assoc :class (:className properties)))
        attributes (s/join " " (for [[k v] attributes
                                     :when (and (not (fn? v)) (seq (str v)))]
                                 (str (name k) "=\"" v "\"")))]
    (str "<" (name tag) (when (seq attributes)
                          (str " " attributes))
         ">"
         (s/join (map hiccup->str children))
         "</" (name tag) ">")))

(defn hiccup->str [hiccup]
  (cond
    (component? hiccup)
    (hiccup->str (apply (first hiccup) (rest hiccup)))

    (hiccup? hiccup)
    (html->str hiccup)

    (seq? hiccup)
    (s/join (map hiccup->str hiccup))

    :else
    (str hiccup)))

(defonce request-refresh (atom false))
(def ^:dynamic *refresh* false)

(def re-component-name #"\$")

(defn component-name [f]
  (s/replace (or (.-name f) "<anonymous>") re-component-name "."))

(defn should-component-update? [node state]
  (not (and node (= (node-state node "state") state))))

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
  (let [node (when (= f (node-state node "component"))
               node)
        state (vec state)
        should-component-update? (:should-component-update? opts should-component-update?)]
    (if (or *refresh* (should-component-update? node state))
      (let [time? (or TIME_COMPONENTS (and (:root opts) TIME_FRAME))
            opts (merge (meta f) opts)
            component-name (component-name f)
            render-start (.now js/Date)]
        (when DEBUG
          (.debug js/console component-name node (s/trim (pr-str state))))
        (let [node (hiccup->dom! node (apply f state))
              render-time (- (.now js/Date) render-start)]
          (set-node-state! node "render-time" render-time)
          (when time?
            (.info js/console component-name render-time "ms"))
          (doto node
            (component-callbacks! opts (node-state node "state") state)
            (set-node-state! "component" f)
            (set-node-state!  "state" state))))
      node)))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn register-top-level-event-handlers! [node]
  (doseq [handler ["onclick" "ondblclick" "onkeydown" "onblur" "onchange"]]
    (aset node handler
          (fn [event]
            (when-let [f (node-state (.-target event) handler)]
              (f event))))))

;; Public API

(defn render-str
  "Renders component f to a HTML string."
  [f & state]
  (hiccup->str (vec (cons f state))))

(defn render-root!
  "Passes all state to f which should return a Hiccup-style tree,
  which is added or reconciled below the provided DOM node as a single
  child."
  [node f & state]
  (binding [*refresh* (compare-and-set! request-refresh true false)
            *mounted-nodes* (atom [])]
    (let [current-node (.-firstChild node)
          new-node (apply component->dom! current-node {:root true} f state)]
      (when-not (= new-node current-node)
        (register-top-level-event-handlers! new-node)
        (doto node
          (aset "innerHTML" "")
          (.appendChild new-node)))
      (doseq [f @*mounted-nodes*]
          (f)))))

(defn render!
  "Render loop using requestAnimationFrame and IWatch to track state
  changes, usually on atoms. Derefences both f and state before
  passing them to render-root! on state change."
  [node f & state]
  (let [f (maybe-deref f)
        tick-requested? (atom false)
        tick (fn []
               (reset! tick-requested? false)
               (apply render-root! node f (mapv maybe-deref state)))
        request-tick! #(when (compare-and-set! tick-requested? false true)
                         (js/requestAnimationFrame tick))]
    (doseq [w (filter #(satisfies? IWatchable %) state)]
      (-add-watch w ::tick request-tick!))
    (request-tick!)))

(defn refresh!
  "Forces a full refersh of the DOM."
  []
  (.info js/console "refresh!")
  (reset! request-refresh true))
