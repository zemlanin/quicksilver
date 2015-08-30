(defproject quicksilver "0.1.0-SNAPSHOT"
  :description "Notifications server for blackwidow"
  :url "http://example.com/FIXME"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [http-kit "2.1.19"]
    [compojure "1.1.5"]
    [ring/ring-devel "1.1.8"]
    [ring/ring-core "1.1.8"]
  ]
  :main ^:skip-aot quicksilver.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
