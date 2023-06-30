(ns trip-planner.core
  (:require
   [clojure.core.async :refer [<!!]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [morse.api :as t]
   [morse.handlers :as h]
   [morse.polling :as p]
   [trip-planner.config :refer [token]]
   [trip-planner.db :as db :refer [get-trips]]
   [trip-planner.helpers :as helpers :refer [get-text]]
   [trip-planner.state :refer [assoc-state-with-user get-bot-state get-handler
                               get-state-from-request initial-state]])
  (:gen-class))

(s/def :chat/id int?)
(s/def ::chat (s/keys :req-un [:chat/id]))
(s/def ::message (s/keys :req-un [::chat]))
(s/def ::request (s/keys :req-un [::message]))
(s/def :request/request-or-message (s/or :message ::message
                                         :request ::request))

(defn merge-data
  "Merges data field of two states"
  [result new-state])

(defn validate-request
  [request state]
  (if-let [spec (:spec state)]
    (s/valid? spec (get-text request))
    true))

;; views

(defn format-trip [{:keys [trips/id trips/date trips/location users/username]}]
  (str "*когда:* " date " "
    "*где:* " location " "
    "*кто:* @" username " "
    ""))

(comment
  (-> (get-trips) :data first format-trip))

;; handlers
(h/defhandler handler
  (h/command "start" {{id :id :as chat} :chat}
             (t/send-text token id "Привет! Я могу помочь тебе запланировать поездку и пошерить ее с друзьями"))

    (h/command "add-trip" {{id :id :as chat} :chat :as message}
             (assoc-state-with-user message (get-bot-state :add-trip-where)))

  (h/command-fn "list-trips"
                (fn [{{id :id :as chat} :chat}]
                  (t/send-text token id {:parse_mode "Markdown"} (clojure.string/join "\n" (map format-trip (:data (get-trips)))))))

  (h/command-fn "help"
                (fn [{{id :id :as chat} :chat :as request}]
                  (log/info "Help was requested in " request)
                  (t/send-text token id "/add-trip - добавить поездку
/list-trips - показать все поездки
/cancel - вернуться в главное меню")))

  (h/command "cancel" {{id :id} :chat :as chat}
             (assoc-state-with-user chat initial-state)
             (t/send-text token id "отменяем.."))

  (fn [request] (when-let [current-handler (get-handler request)]
                  (if (validate-request request (get-state-from-request request))
                    (current-handler request)
                    (when-let [fail-handler (:fail (get-state-from-request request))]
                      (fail-handler request)))))

  (h/defhandler catch-all
    (h/message-fn (fn [{{id :id} :chat :as message}]
                    (t/send-text token id
                                 "Неизвестная команда"))))
  )

;; middleware

(defn logger [request]
  (log/info "new request: " request)
  request)

(defn get-or-save-user
  "Gets a user from request and saves to db, if missing"
 [request]
  (let [user-from-request (helpers/get-user request)
        user (-> user-from-request :id (db/get-user))]
    (when-not user
      (db/save-user user-from-request)))
  request)

(def middleware [logger get-or-save-user])

(defn apply-middleware [request mws]
  (reduce (fn [req mw] (mw req)) request mws))

;; handle request
(defn handle-request
  [request]
  (try (-> (apply-middleware request middleware)
           (handler))
       (catch Exception e (log/warn "Exception occured while handling request" e))))

(defn -main
  [& args]
  (when (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (System/exit 1))
  (println "Starting the trip-")
  (<!! (p/start token handle-request)))
