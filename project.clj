(defproject trip-planner "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [environ             "1.1.0"]
                 [morse               "0.2.4"]
                 [commons-io "2.4"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/tools.namespace "0.2.3"]]

  :plugins [[lein-environ "1.1.0"]
            [lein-auto "0.1.3"]]

  :aliases {"dev" ["run" "-m" "trip-planner.dev"]}            

  :main ^:skip-aot trip-planner.core
  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
