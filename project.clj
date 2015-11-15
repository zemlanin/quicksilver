(defproject quicksilver "0.1.0-SNAPSHOT"
  :description "Notifications server for blackwidow"
  :url "http://example.com/FIXME"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying"}
  :dependencies [
    [org.clojure/clojure "1.7.0"]
    [org.clojure/clojurescript "1.7.170"]
    [org.clojure/java.jdbc "0.4.1"]
    [org.clojure/data.json "0.2.6"]
    [org.postgresql/postgresql "9.2-1004-jdbc4"]
    [korma "0.4.2" :exclusions [c3p0 postgresql]]
    [http-kit "2.1.19"]
    [compojure "1.4.0"]
    [ring "1.4.0"]
    [ring/ring-anti-forgery "1.0.0"]
    [jarohen/nomad "0.7.2"]
    [org.clojure/core.match "0.3.0-alpha4"]
    [com.mchange/c3p0 "0.9.5.1"]
    [clj-time "0.11.0"]
    [camel-snake-kebab "0.3.2"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [hiccup "1.0.5"]
    [com.draines/postal "1.11.3"]
    [prismatic/schema "1.0.1"]
    [com.taoensso/carmine "2.12.0"]
  ]
  :plugins [
    [lein-cljsbuild "1.1.1"]
    [lein-figwheel "0.5.0-1"]
  ]
  :cljsbuild {
    :builds [{:id "dev" 
              :source-paths ["src-cljs/"]
              :figwheel true
              :compiler {:main "quicksilver.core"
                         :asset-path "js/compiled/out"
                         :output-to "resources/public/js/compiled/quicksilver.js"
                         :output-dir "resources/public/js/compiled/out"}}
             {:id "min"
              :source-paths ["src-cljs/"]
              :compiler {:output-to "resources/public/js/compiled/quicksilver.js"
                         :main "quicksilver.core"
                         :optimizations :simple
                         :pretty-print false}}]
  }
  :figwheel {
    ; :http-server-root "public" ;; this will be in resources/
    ; :server-port 3449          ;; default
    ; :server-ip   "0.0.0.0"     ;; default

    ;; CSS reloading (optional)
    ;; :css-dirs has no default value 
    ;; if :css-dirs is set figwheel will detect css file changes and
    ;; send them to the browser
    :css-dirs ["resources/public/css"]

    ;; Server Ring Handler (optional)
    ;; if you want to embed a ring handler into the figwheel http-kit
    ;; server
    :ring-handler quicksilver.core/my-app-reload

    ;; Clojure Macro reloading
    ;; disable clj file reloading
    ; :reload-clj-files false
    ;; or specify which suffixes will cause the reloading
    ; :reload-clj-files {:clj true :cljc false}

    ;; To be able to open files in your editor from the heads up display
    ;; you will need to put a script on your path.
    ;; that script will have to take a file path and a line number
    ;; ie. in  ~/bin/myfile-opener
    ;; #! /bin/sh
    ;; emacsclient -n +$2 $1
    ;;
    ; :open-file-command "myfile-opener"

    ;; if you want to disable the REPL
    ;; :repl false

    ;; to configure a different figwheel logfile path
    ;; :server-logfile "tmp/logs/figwheel-logfile.log" 

    ;; Start an nREPL server into the running figwheel process
    ;; :nrepl-port 7888

    ;; Load CIDER, refactor-nrepl and piggieback middleware
    ;;  :nrepl-middleware ["cider.nrepl/cider-middleware"
    ;;                     "refactor-nrepl.middleware/wrap-refactor"
    ;;                     "cemerick.piggieback/wrap-cljs-repl"]
  }
  :main ^:skip-aot quicksilver.core
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :repl-options {:init-ns quicksilver.core}
  :profiles {:uberjar {:aot :all}})
