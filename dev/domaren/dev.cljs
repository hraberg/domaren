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
(defn edited-value [v] (reset! value v))
(defn start-edit [id title]
  (edited-value title)
  (swap! todos assoc-in [id :editing] true))
(defn stop-edit [id]
  (swap! todos assoc-in [id :editing] false))
(defn select-filter [name] (reset! filt name))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :done] v)))
(defn clear-done [] (swap! todos mmap remove #(get-in % [1 :done])))

(defonce init (do
                (add-todo "Rename Cloact to Reagent")
                (add-todo "Add undo demo")
                (add-todo "Make all rendering async")
                (add-todo "Allow any arguments to component functions")
                (complete-all true)))

(def KEYS {:enter 13 :esc 27})

(defn todo-input [{:keys [id class placeholder onsave onstop val]}]
  (let [stop #(do (edited-value "")
                  (if onstop (onstop)))
        save #(do (if-not (empty? val) (onsave val))
                  (stop))
        keymap {(:enter KEYS) save
                (:esc KEYS) stop}]
    [:input {:id id
             :class class
             :placeholder placeholder
             :type "text" :value val :onblur (if onstop stop save)
             :oninput #(-> % .-target .-value edited-value)
             :onkeydown #(some-> % .-which keymap (apply []))}]))

(def todo-edit (with-meta todo-input
                 {:did-mount #(.focus %)}))

(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name filt) "selected")
                     :onclick #(select-filter name)})]
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
    [:label {:ondblclick #(start-edit id title)} title]
    [:button.destroy {:onclick #(delete id)}]]
   (when editing
     [todo-edit {:class "edit"
                 :onsave #(save id %)
                 :onstop #(stop-edit id)
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
                    :val (when-not (some :editing items)
                           val)}]]
      (when (seq items)
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
