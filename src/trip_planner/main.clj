(ns trip-planner.main
  (:require
   [nrepl.server :refer [start-server]]
   [trip-planner.config :refer [bot repl-port]]
   [trip-planner.polling :refer [app]]))

(defn -main
  []
  (println "Starting bot...")
  (start-server :bind "0.0.0.0" :port repl-port)
  (app bot))
