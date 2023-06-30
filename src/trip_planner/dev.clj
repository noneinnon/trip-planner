(ns trip-planner.dev
  (:require
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [morse.polling :as p]
   [trip-planner.config :refer [token]]
   [trip-planner.core :refer [handle-request handler]]))

(def chan (atom nil))

(defn dev
  []
  (when @chan
    (p/stop @chan))
  (reset! chan (p/start token handler)))

(defn start [] (reset! chan (p/start token handle-request)))

(defn stop []
  (when @chan (p/stop @chan))
  (reset! chan nil))

(defn -main []
  (println "stopping bot")
  (stop)
  (println "starting bot")
  (refresh :after 'trip-planner.dev/start))

(comment
  (stop)
  (-main)
  (refresh-all :after 'trip-planner.dev/start))
