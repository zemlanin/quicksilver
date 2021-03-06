(defproject quicksilver "0.1.0-SNAPSHOT"
  :description "Notifications server for blackwidow"
  :url "http://example.com/FIXME"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.postgresql/postgresql "9.2-1004-jdbc4"]
                 [korma "0.4.2" :exclusions [c3p0 postgresql]]
                 [http-kit "2.1.19"]
                 [compojure "1.4.0"]
                 [ring "1.4.0"]
                 [jarohen/nomad "0.7.2"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.mchange/c3p0 "0.9.5.1"]
                 [clj-time "0.11.0"]
                 [camel-snake-kebab "0.3.2"]
                 [org.clojure/core.async "0.2.391"]]

  :source-paths ["src"]

  :main ^:skip-aot quicksilver.core
  :target-path "target/%s"
  :jvm-opts ["-Duser.timezone=UTC"]
  :repl-options {:init-ns quicksilver.core}
  :profiles {:uberjar {:aot :all}})
