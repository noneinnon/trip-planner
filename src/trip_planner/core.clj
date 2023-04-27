(ns trip-planner.core
  (:require [clojure.core.async :refer [<!! <! go]]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [morse.handlers :as h]
            [morse.polling :as p]
            [morse.api :as t]
            [clojure.tools.logging :as log])
  (:gen-class))

(def token (env :telegram-token))

(h/defhandler handler
  (h/command-fn "start"
                (fn [{{id :id :as chat} :chat}]
                  (println "Bot joined new chat: " chat)
                  (t/send-text token id "Welcome to trip-planner!")))

  (h/command-fn "help"
                (fn [{{id :id :as chat} :chat}]
                  (println "Help was requested in " chat)
                  (t/send-text token id "Help is on the way")))

  (h/message-fn
   (fn [{{id :id} :chat :as message}]
     (log/info "message " message)
     (t/send-text token id
                  {:parse_mode "Markdown"}
        ; :reply_markup {:inline_keyboard [[{:text "some button" :callback_data 123}]]}} 
                  "privet poka!"))))

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))
  (println "Starting the trip-")
  (<!! (p/start token handler)))

(comment
  (+ 1 1))