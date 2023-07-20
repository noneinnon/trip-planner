(ns trip-planner.config
  (:require    [environ.core :refer [env]]
               [telegrambot-lib.core :as tbot]))

(def repl-port (or (env :repl-port) 54321))

;; BOT_TOKEN must be defined as env variable
(def bot (if (env :bot-token)
           (tbot/create)
           (do (println "BOT_TOKEN is not set")
               (System/exit 1))))
