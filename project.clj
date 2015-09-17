(defproject quicksilver "0.1.0-SNAPSHOT"
  :description "Notifications server for blackwidow"
  :url "http://example.com/FIXME"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [org.clojure/java.jdbc "0.4.1"]
    [org.clojure/data.json "0.2.6"]
    [org.postgresql/postgresql "9.4-1201-jdbc41"]
    [korma "0.4.2"]
    [http-kit "2.1.19"]
    [compojure "1.4.0"]
    [ring "1.4.0"]
    [jarohen/nomad "0.7.2"]
    [org.clojure/core.match "0.3.0-alpha4"]
  ]
  :main quicksilver.core
  :aot [quicksilver.core]
  :uberjar {:aot :all}
  :target-path "target/%s"
  :repl-options {:init-ns quicksilver.core}
  :profiles {:uberjar {:aot :all}})
