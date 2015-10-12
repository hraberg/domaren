(defproject domaren "0.1.0-SNAPSHOT"
  :description "Domaren is a ClojureScript incremental-dom style library."
  :url "http://hraberg.github.io/domaren/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.0" :exclusions [org.clojure/clojure
                                                org.codehaus.plexus/plexus-utils
                                                org.clojure/tools.reader]]]
  :profiles {:dev {:resource-paths ["examples/todomvc/resources"
                                    "examples/devcards/resources"]
                   :dependencies [[com.cemerick/piggieback "0.2.1"
                                   :exclude [org.clojure/clojurescript]]
                                  [org.clojure/tools.nrepl "0.2.11"]
                                  [devcards "0.2.0-3" :exclusions [cljsjs/react]]
                                  [cljsjs/react "0.13.3-0"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :pedantic? :abort
  :source-paths ["src"]
  :clean-targets ^{:protect false} ["examples/todomvc/resources/public/js/compiled"
                                    "examples/vdom-benchmark-domaren/web/js/compiled"
                                    "examples/devcards/resources/public/js/compiled"
                                    "target"]
  :cljsbuild {:builds [{:id "todomvc"
                        :source-paths ["src" "examples/todomvc/src"]
                        :figwheel {:on-jsload "domaren.core/refresh!"
                                   :css-dirs ["examples/todomvc/resources/public/node_modules/todomvc-app-css/"
                                              "examples/todomvc/resources/public/node_modules/todomvc-common/"]}
                        :compiler {:main todomvc.app
                                   :asset-path "js/compiled/out"
                                   :output-to "examples/todomvc/resources/public/js/compiled/todomvc.js"
                                   :output-dir "examples/todomvc/resources/public/js/compiled/out"
                                   :source-map-timestamp true}}
                       {:id "vdom-benchmark-domaren"
                        :source-paths ["src" "examples/vdom-benchmark-domaren/src"]
                        :compiler {:main vdom-benchmark-domaren.core
                                   :asset-path "js/compiled/out"
                                   :output-to "examples/vdom-benchmark-domaren/web/js/compiled/vdom-benchmark-domaren.js"
                                   :optimizations :simple}}
                       {:id "devcards"
                        :source-paths ["src" "examples/devcards/src"]
                        :figwheel {:devcards true
                                   :css-dirs ["examples/devcards/resources/public/css"]}
                        :compiler {:main domaren-devcards.core
                                   :asset-path "js/devcards/compiled/out"
                                   :output-to "examples/devcards/resources/public/js/devcards/compiled/domaren-devcards.js"
                                   :output-dir "examples/devcards/resources/public/js/devcards/compiled/out"
                                   :source-map-timestamp true}}]}
  :figwheel {:nrepl-port 7888
             :nrepl-middleware ["cider.nrepl/cider-middleware"
                                "cemerick.piggieback/wrap-cljs-repl"]})
