(defproject quicksilver "0.1.0-SNAPSHOT"
  :description "Notifications server for blackwidow"
  :url "http://example.com/FIXME"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying"}
  :dependencies [
    [org.clojure/clojure "1.7.0"]
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
  :main ^:skip-aot quicksilver.core
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :repl-options {:init-ns quicksilver.core}
  :profiles {:uberjar {:aot :all}})
