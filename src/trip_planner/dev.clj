(ns trip-planner.dev
  (:require
   [clojure.tools.namespace.repl :refer [refresh-all]]
   [nrepl.server :refer [start-server]]
   [trip-planner.config :refer [bot]]
   [trip-planner.polling :refer [app]]))

(def repl-server (atom nil))

(def repl-port 54321)

(defonce running (atom nil))

(defn start []
  (reset! running (app bot)))

(defn stop []
  (when (future? @running)
    (future-cancel @running)))

(defn -main []
  (println "stopping bot")
  (stop)
  (reset! repl-server (start-server :bind "0.0.0.0" :port repl-port))
  (println "starting bot")
  (start))

(comment
  (-main)
  (refresh-all :after 'trip-planner.dev/start))
