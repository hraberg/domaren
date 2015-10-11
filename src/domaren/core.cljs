(ns domaren.core
    (:require [clojure.string :as s]))

(def DEBUG false)
(def TIME_COMPONENTS false)
(def TIME_FRAME false)

(defn debug [& args]
  (when DEBUG
    (.apply (.-debug js/console) js/console (into-array (map #(if ((some-fn keyword? coll?) %)
                                                                (pr-str %)
                                                                %) args)))))

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

(defn ^boolean hiccup? [x]
  (and (instance? PersistentVector x)
       (keyword? (first x))))

(defn ^boolean component? [x]
  (and (instance? PersistentVector x)
       (instance? js/Function (first x))))

(def elements #js {})

(defn create-element [node tag]
  (if (= tag (node-state node "tag"))
    (do (debug :reusing-element node)
        node)
    (doto (-> (or (aget elements tag)
                  (aset elements tag (.createElement js/document tag)))
              (.cloneNode false))
      (->> (debug :create-element))
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

(defn remove-node [node]
  (debug :remove-node (.-parentNode node) node)
  (.remove node))

(defn remove-siblings-starting-at! [node]
  (when node
    (while (.-nextSibling node)
      (remove-node (.-nextSibling node)))
    (remove-node node)))

(defn event-handlers [attributes]
  (into {} (filter #(instance? js/Function (val %)) attributes)))

;; From https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj
(def ^{:doc "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn hiccup-key [hiccup]
  (some-> hiccup meta :key str))

(defn reconcile! [node child old-child hiccup]
  (debug :reconcile (or (hiccup-key hiccup) "") child old-child)
  (let [child (if (and child old-child
                       (not= child old-child))
                (do (debug :insert-before node old-child child)
                    (.insertBefore node old-child child))
                child)
        new-child (hiccup->dom! child hiccup)]
    (cond
      (and child (not= child new-child))
      (do (debug :replace-child node new-child child)
          (.replaceChild node new-child child))

      (not child)
      (do (debug :append-child node new-child)
          (.appendChild node new-child)))
    new-child))

(defn align-children! [node children]
  (let [key-map (or (node-state node "keys") #js {})
        new-key-map #js {}]
    (loop [[h & hs] children stack nil child (.-firstChild node)]
      (cond
        (seq? h)
        (recur h (cons hs stack) child)

        (and (nil? h) (first stack))
        (recur (first stack) (next stack) child)

        (nil? h)
        (remove-siblings-starting-at! child)

        :else
        (let [key (hiccup-key h)
              old-child (aget key-map key)
              _  (when-not old-child
                   (some->> (node-state child "key") (js-delete key-map)))
              child (cond->> (reconcile! node child old-child h)
                      key (aset new-key-map key))]
          (recur hs stack (.-nextSibling child)))))
    (set-node-state! node "keys" new-key-map)))

(def normalize-tag
  (memoize
   (fn normalize-tag [tag attributes]
     (let [[_ tag id class] (re-find re-tag (name tag))
           class (:class attributes (some-> class (.replace "." " ")))
           id (attributes :id id)
           handlers (event-handlers attributes)
           form-properties (select-keys attributes [:value :checked :selected :selectedIndex])
           attributes (cond-> (apply dissoc attributes :id
                                     (concat (keys handlers) (keys form-properties)))
                        id (assoc :id id)
                        class (assoc :class class))]
       {:tag tag :attributes attributes :properties form-properties :handlers handlers
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
      (register-event-handlers! handlers)
      (set-node-state! "key" (hiccup-key hiccup)))))

(defn ^boolean text-node? [node]
  (some-> node .-nodeType (== (.-TEXT_NODE js/Node))))

(defn text->dom! [node text]
  (if (text-node? node)
    (do (debug :reusing-text-node (.-nodeValue node) text)
        (cond-> node
          (not= (.-nodeValue node) text) (doto (aset "nodeValue" text))))
    (doto (.createTextNode js/document text)
      (->>  (debug :create-text-node)))))

(defn hiccup->dom! [node hiccup]
  (cond
    (hiccup? hiccup)
    (html->dom! node hiccup)

    (component? hiccup)
    (apply component->dom! node (meta hiccup) hiccup)

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
                                     :when (and (not (instance? js/Function v))
                                                (seq (str v)))]
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

(defn component-name [f]
  (.replace (or (.-name f) "<anonymous>") "$" "."))

(defn ^boolean should-component-update? [node state]
  (not (and node (= (node-state node "state") state))))

(defn ^boolean component-will-mount? [node]
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
        (debug :component component-name node state)
        (let [node (hiccup->dom! node (apply f state))
              render-time (- (.now js/Date) render-start)]
          (set-node-state! node "render-time" render-time)
          (when time?
            (.info js/console component-name render-time "ms"))
          (doto node
            (component-callbacks! opts (node-state node "state") state)
            (set-node-state! "component" f)
            (set-node-state! "state" state))))
      (do (debug :should-not-update node)
          node))))

(defn maybe-deref [x]
  (cond-> x (satisfies? IDeref x) deref))

(defn register-top-level-event-handlers! [node]
  (doseq [handler ["onclick" "ondblclick" "onkeydown" "onblur" "onchange"]]
    (aset node handler
          (fn [event]
            (when-let [f (node-state (.-target event) handler)]
              (f event))))))

;; Public API

(defn remove-root!
  "Removes all nodes created under node."
  [node]
  (some-> node .-firstChild remove-siblings-starting-at!))

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
          f (cond-> f
              (vector? f) constantly)
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
  passing them to render-root! on state change.

  Returns a function that takes no arguments that will uninstall the
  render loop and remove the generated DOM."
  [node f & state]
  (let [f (maybe-deref f)
        tick-requested? (atom false)
        running? (atom true)
        tick #(when @running?
                (reset! tick-requested? false)
                (apply render-root! node f (mapv maybe-deref state)))
        request-tick! #(when (and @running?
                                  (compare-and-set! tick-requested? false true))
                         (js/requestAnimationFrame tick))
        watchables (filter #(satisfies? IWatchable %) state)
        stop-render! (fn stop-render! []
                       (reset! running? false)
                       (doseq [w watchables]
                         (-remove-watch w ::tick))
                       (remove-root! node))]
    (doseq [w watchables]
      (-add-watch w ::tick request-tick!))
    (request-tick!)
    stop-render!))

(defn refresh!
  "Forces a full refersh of the DOM."
  []
  (.info js/console "refresh!")
  (reset! request-refresh true))
