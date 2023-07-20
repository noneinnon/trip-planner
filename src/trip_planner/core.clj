(ns trip-planner.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [telegrambot-lib.core :as tbot]
   [trip-planner.config :refer [bot]]
   [trip-planner.db :as db :refer [get-trips db-spec]]
   [trip-planner.helpers :as helpers :refer [get-text get-chat-id]]
   [trip-planner.state :refer [state-handler assoc-state-with-user get-bot-state
                               initial-state]])
  (:gen-class))

(s/def :chat/id int?)
(s/def ::callback_query map?)
(s/def ::callback-query-update (s/keys :req-un [::callback_query]))
(s/def ::chat (s/keys :req-un [:chat/id])) (s/def ::message (s/keys :req-un [::chat]))
(s/def ::request (s/keys :req-un [::message]))

(s/def :request/update (s/or :message ::message
                             :request ::request
                             :callback_query ::callback-query-update))

;; views
(defn format-trip [{:keys [trips/date trips/location users/username]}]
  (str "*когда:* " date " "
       "*где:* " location " "
       "*кто:* @" username " "
       ""))

;; handlers

(defn list-today-handler [request]
  (let [trips (get-trips db-spec :today
                         {:limit 10 ;; TODO come up with a better solution
                          :offset 0})]
    (if (not-empty trips)
      (tbot/send-message bot {:chat_id (get-chat-id request) :parse_mode "Markdown" :text (clojure.string/join "\n" (map format-trip trips))})
      (tbot/send-message bot (get-chat-id request) "Поездок на сегодня не найдено 🐲"))))

(defn list-tomorrow-handler [request]
  (let [trips  (get-trips db-spec :tomorrow
                          {:limit 10
                           :offset 0})]
    (if (not-empty trips)
      (tbot/send-message bot {:chat_id (get-chat-id request) :parse_mode "Markdown" :text (clojure.string/join "\n" (map format-trip trips))})
      (tbot/send-message bot (get-chat-id request) "Поездок на завтра не найдено 🐷"))))

(defn list-next-week-handler [request]
  (let [trips (get-trips db-spec :next-week
                         {:limit 10
                          :offset 0})]
    (if (not-empty trips)
      (tbot/send-message bot {:chat_id (get-chat-id request) :parse_mode "Markdown" :text (clojure.string/join "\n" (map format-trip trips))})
      (tbot/send-message bot (get-chat-id request) "Не следующей неделе нету поездок 🐴"))))

(defn add-trip-handler [request]
  (assoc-state-with-user request (get-bot-state :add-trip-where)))

(defn cancel-handler [request]
  (assoc-state-with-user request initial-state)
  (tbot/send-message bot (get-chat-id request) "отменяем.."))

(defn start-handler [request]
  (tbot/send-message bot (get-chat-id request) "Привет! Я могу помочь тебе запланировать поездку и пошерить ее с друзьями"))

(defn help-handler [request]
  (log/info "Help was requested in " request)
  (tbot/send-message bot (str "/add-trip - добавить поездку\n"
                              "/list-trips - показать все поездки\n"
                              "/cancel - вернуться в главное меню") (get-chat-id request)))

(defn new-handler [bot msg]
  (log/info "recieved updated" msg)
  (let [text (get-text msg)]
    (when-not (nil? text)
      (cond
        (str/starts-with? text "/help") (help-handler msg)
        (str/starts-with? text "/start") (start-handler msg)
        (str/starts-with? text "/today") (list-today-handler msg)
        (str/starts-with? text "/tomorrow") (list-tomorrow-handler msg)
        (str/starts-with? text "/next-week") (list-next-week-handler msg)
        (str/starts-with? text "/add-trip") (add-trip-handler msg)
        (str/starts-with? text "/cancel") (cancel-handler msg)
        :else (state-handler msg)))))

;; middleware

(defn get-or-save-user
  "Gets a user from request and saves to db, if missing"
  [request]
  (let [user-from-request (helpers/get-user request)
        user (-> user-from-request :id (db/get-user))]
    (when-not user
      (db/save-user user-from-request)))
  request)

(def middleware [get-or-save-user])

(defn apply-middleware [request mws]
  (reduce (fn [req mw] (mw req)) request mws))
