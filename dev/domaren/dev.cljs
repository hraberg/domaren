(ns ^:figwheel-always domaren.dev
    (:require [cljs.reader :as r]
              [clojure.string :as s]
              [domaren.core]))

;; Based on https://github.com/reagent-project/reagent/blob/master/examples/todomvc/src/todomvc/core.cljs

(enable-console-print!)

(set! domaren.core/DEBUG false)
(set! domaren.core/TIME_COMPONENTS false)
(set! domaren.core/TIME_FRAME true)

(defonce todos (-add-watch (atom (into (sorted-map)
                                       (some->> "todos-domaren"
                                                (.getItem js/localStorage)
                                                r/read-string)))
                           :storage
                           (fn [_ _ newval]
                             (.setItem js/localStorage "todos-domaren"
                                       (pr-str (reduce-kv (fn [m k v]
                                                            (assoc m k (dissoc v :editing)))
                                                          {} @todos))))))
(def filters (array-map :all identity
                        :active (complement :completed)
                        :completed :completed))

(defonce filt (atom (key (first filters))))
(defonce value (atom ""))
(defonce counter (atom (or (some-> @todos last key) 0)))

(defn add-todo [text]
  (let [id (swap! counter inc)]
    (swap! todos assoc id {:id id :title text :completed false})))

(defn toggle [id] (swap! todos update-in [id :completed] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))
(defn edited-value [v] (reset! value v))
(defn start-edit [id title]
  (edited-value title)
  (swap! todos assoc-in [id :editing] true))
(defn stop-edit [id]
  (swap! todos update-in [id] dissoc :editing))
(defn select-filter [name] (reset! filt name))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :completed] v)))
(defn clear-completed [] (swap! todos mmap remove #(get-in % [1 :completed])))

(def KEYS {:enter 13 :esc 27})

(defn todo-input [{:keys [class placeholder onsave onstop value]}]
  (let [stop #(do (edited-value "")
                  (if onstop (onstop)))
        save #(do (if-not (empty? value) (onsave value))
                  (stop))
        keymap {(:enter KEYS) save
                (:esc KEYS) stop}]
    [:input {:class class
             :placeholder placeholder
             :type "text" :value value :onblur (if onstop stop save)
             :autofocus true
             :oninput #(-> % .-target .-value edited-value)
             :onkeydown #(some-> % .-which keymap (apply []))}]))

(defn todo-stats [{:keys [filt active completed]}]
  [:div
   [:span.todo-count
    [:strong active] " " (case active 1 "item" "items") " left"]
   [:ul.filters
    (for [f (keys filters)]
      [:li [:a {:class (if (= f filt) "selected")
                :href (str "#/" (name f))}
            (s/capitalize (name f))]])]
   (when (pos? completed)
     [:button.clear-completed {:onclick clear-completed}
      "Clear completed " completed])])

(defn todo-item [{:keys [editing id completed title]} value]
  [:li {:class (str (if completed "completed ")
                    (if editing "editing"))}
   [:div.view
    [:input.toggle {:type "checkbox" :checked completed
                    :onchange #(toggle id)}]
    [:label {:ondblclick #(start-edit id title)} title]
    [:button.destroy {:onclick #(delete id)}]]
   (when editing
     ^{:did-mount #(.focus %)}
     [todo-input {:class "edit"
                  :onsave #(save id %)
                  :onstop #(stop-edit id)
                  :value value}])])

(defn todo-app [todos filt value]
  (let [items (vals todos)
        completed (->> items (filter :completed) count)
        active (- (count items) completed)]
    [:div
     [:section.todoapp
      [:header.header
       [:h1 "todos"]
       [todo-input {:class "new-todo"
                    :placeholder "What needs to be done?"
                    :onsave add-todo
                    :value (when-not (some :editing items)
                             value)}]]
      (when (seq items)
        [:div
         [:section.main
          [:input.toggle-all {:type "checkbox" :checked (zero? active)
                              :onchange #(complete-all (pos? active))}]
          [:label {:for "toggle-all"} "Mark all as complete"]
          [:ul.todo-list
           (for [todo (filter (filters filt) items)]
             ^{:key (:id todo)} [todo-item todo value])]]
         [:footer.footer
          [todo-stats {:active active :completed completed :filt filt}]]])]
     [:footer.info
      [:p "Double-click to edit a todo"]
      [:p "Created by Håkan Råberg"]]]))

(defn on-hashchange []
  (let [hash (some-> js/location .-hash (subs 2) keyword)]
    (if (filters hash)
      (select-filter hash)
      (aset js/location "hash" (str "/" (name @filt))))))

(.addEventListener js/window "hashchange" on-hashchange)
(on-hashchange)

(domaren.core/render! (.getElementById js/document "app")
                      #'todo-app
                      todos filt value)
