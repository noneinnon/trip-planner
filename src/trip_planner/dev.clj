(ns trip-planner.dev
  (:require [trip-planner.core :refer [token handler]]
            [morse.polling :as p]
            [morse.api :as t]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]))

(def chan (atom nil))
; (def chan (p/start token handler)) ;; start in repl
; (p/stop chan) ;; stop bot
;; something
(defn dev
  []
  (when @chan
    (p/stop @chan))
  (reset! chan (p/start token handler)))

(defn start [] (reset! chan (p/start token handler)))

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
	(-main1)
  (refresh-all :after 'trip-planner.dev/start))