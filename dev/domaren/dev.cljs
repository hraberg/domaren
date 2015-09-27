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
(defonce edited-todo (atom nil))
(defonce counter (atom (or (some-> @todos last key) 0)))

(defn add-todo [title]
  (let [id (swap! counter inc)]
    (swap! todos assoc id {:id id :title title :completed false})))

(defn toggle [id] (swap! todos update-in [id :completed] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))
(defn edit-title [v] (swap! edited-todo assoc :title  v))
(defn start-edit [id]
  (reset! edited-todo (@todos id)))
(defn stop-edit []
  (reset! edited-todo nil))
(defn select-filter [name] (reset! filt name))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :completed] v)))
(defn clear-completed [] (swap! todos mmap remove #(get-in % [1 :completed])))

(def KEYS {:enter 13 :esc 27})

(defn todo-input [{:keys [onsave cancel-on-blur value] :as props}]
  (let [save #(do (if-not (empty? value) (onsave value))
                  (stop-edit))
        keymap {(:enter KEYS) save
                (:esc KEYS) stop-edit}]
    [:input (merge (select-keys props [:class :placeholder :value])
                   {:autofocus true
                    :onblur (if cancel-on-blur stop-edit save)
                    :oninput #(-> % .-target .-value edit-title)
                    :onkeydown #(some-> % .-which keymap (apply []))})]))

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

(defn todo-item [{:keys [id completed title]} edited-todo]
  (let [editing (= id (:id edited-todo))]
    [:li {:class (str (if completed "completed ")
                      (if editing "editing"))}
     [:div.view
      [:input.toggle {:type "checkbox" :checked completed
                      :onchange #(toggle id)}]
      [:label {:ondblclick #(start-edit id)} title]
      [:button.destroy {:onclick #(delete id)}]]
     (when editing
       ^{:did-mount #(.focus %)}
       [todo-input {:class "edit"
                    :value (:title edited-todo)
                    :cancel-on-blur true
                    :onsave #(save id %)}])]))

(defn todo-app [todos filt edited-todo]
  (let [items (vals todos)
        completed (->> items (filter :completed) count)
        active (- (count items) completed)]
    [:div
     [:section.todoapp
      [:header.header
       [:h1 "todos"]
       [todo-input {:class "new-todo"
                    :placeholder "What needs to be done?"
                    :value (when-not (:id edited-todo)
                             (:title edited-todo))
                    :onsave add-todo}]]
      (when (seq items)
        [:div
         [:section.main
          [:input.toggle-all {:type "checkbox" :checked (zero? active)
                              :onchange #(complete-all (pos? active))}]
          [:label {:for "toggle-all"} "Mark all as complete"]
          [:ul.todo-list
           (for [todo (filter (filters filt) items)]
             ^{:key (:id todo)} [todo-item todo edited-todo])]]
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
                      todos filt edited-todo)
