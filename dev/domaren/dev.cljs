(ns ^:figwheel-always domaren.dev
    (:require [domaren.core]))

;; Based on https://github.com/reagent-project/reagent/blob/master/examples/todomvc/src/todomvc/core.cljs

(enable-console-print!)

(set! domaren.core/DEBUG false)
(set! domaren.core/TIME_COMPONENTS false)
(set! domaren.core/TIME_FRAME true)

(defonce todos (atom (sorted-map)))
(defonce filt (atom :all))
(defonce value (atom ""))
(defonce counter (atom 0))

(defn add-todo [text]
  (let [id (swap! counter inc)]
    (swap! todos assoc id {:id id :title text :done false :editing false})))

(defn toggle [id] (swap! todos update-in [id :done] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :done] v)))
(defn clear-done [] (swap! todos mmap remove #(get-in % [1 :done])))

(defonce init (do
                (add-todo "Rename Cloact to Reagent")
                (add-todo "Add undo demo")
                (add-todo "Make all rendering async")
                (add-todo "Allow any arguments to component functions")
                (complete-all true)))

(defn todo-input [{:keys [id title class placeholder onsave onstop val]}]
  (let [stop #(do (reset! domaren.dev/value "")
                  (if onstop (onstop)))
        save #(let [v (-> val str clojure.string/trim)]
                (if-not (empty? v) (onsave v))
                (stop))]
    [:input {:title title
             :id id
             :class class
             :placeholder placeholder
             :type "text" :value val :onblur save
             :onchange #(reset! domaren.dev/value (-> % .-target .-value))
             :onkeydown #(case (.-which %)
                           13 (save)
                           27 (stop)
                           nil)}]))

(def todo-edit (with-meta todo-input
                 {:did-mount #(.focus %)}))

(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name filt) "selected")
                     :onclick #(reset! domaren.dev/filt name)})]
    [:div
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     (when (pos? done)
       [:button#clear-completed {:onclick clear-done}
        "Clear completed " done])]))

(defn todo-item [{:keys [editing id done title]} val]
  [:li {:class (str (if done "completed ")
                    (if editing "editing"))}
   [:div.view
    [:input.toggle {:type "checkbox" :checked done
                    :onchange #(toggle id)}]
    [:label {:onclick #(swap! todos assoc-in [id :editing] true)} title]
    [:button.destroy {:onclick #(delete id)}]]
   (when editing
     [todo-edit {:class "edit" :title title
                 :onsave #(save id %)
                 :onstop #(swap! todos assoc-in [id :editing] false)
                 :val val}])])

(defn todo-app [todos filt val]
  (let [items (vals todos)
        done (->> items (filter :done) count)
        active (- (count items) done)]
    [:div
     [:section#todoapp
      [:header#header
       [:h1 "todos"]
       [todo-input {:id "new-todo"
                    :placeholder "What needs to be done?"
                    :onsave add-todo
                    :val val}]]
      (when (-> items count pos?)
        [:div
         [:section#main
          [:input#toggle-all {:type "checkbox" :checked (zero? active)
                              :onchange #(complete-all (pos? active))}]
          [:label {:for "toggle-all"} "Mark all as complete"]
          [:ul#todo-list
           (for [todo (filter (case filt
                                :active (complement :done)
                                :done :done
                                :all identity) items)]
             ^{:key (:id todo)} [todo-item todo val])]]
         [:footer#footer
          [todo-stats {:active active :done done :filt filt}]]])]
     [:footer#info
      [:p "Double-click to edit a todo"]]]))

(domaren.core/render!
 (.getElementById js/document "app")
 #'todo-app
 todos filt value)

;; (defonce app-state (atom {:text "Hello world!" :count 2}))

;; (defn foo-component [count f]
;;   [:pre {:onclick (fn [evt] (when f (f evt)))} count])

;; (defn render-app [state]
;;   [:div#2.foo.bar {:title "FOO"}
;;    [:h1 (:text state)]
;;    (for [i (shuffle (range 3))]
;;      ^{:key i} [:span i])
;;    ^{:will-mount (fn [node count]
;;                    (.debug js/console "Will Mount" node (.-parentNode node) (pr-str count)))}
;;    [foo-component (* 5 (:count state))]
;;    [foo-component (* 5 (:count state))]
;;    [foo-component (* 2 (:count state))]
;;    [foo-component (* 2 (:count state)) (fn [evt] (js/alert "!"))]
;;    ^{:did-mount (fn [node count]
;;                   (.debug js/console "Did Mount" node (.-parentNode node)  (pr-str count)))}
;;    [foo-component (:count state)]])

;; (domaren.core/render!
;;  (.getElementById js/document "app")
;;  #'render-app
;;  app-state)

;; (comment
;;   (do
;;     (require 'figwheel-sidecar.repl-api)
;;     (figwheel-sidecar.repl-api/cljs-repl))

;;   (reset! app-state {:text "Hello", :count 3}))
