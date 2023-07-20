(ns trip-planner.state
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [telegrambot-lib.core :as tbot]
   [trip-planner.config :refer [bot]]
   [trip-planner.db :as db]
   [trip-planner.helpers :refer [get-chat-id get-text get-user string-to-date]]))

(def ^{:private true} bot-states (atom {}))

(def initial-state {:name :initial :data {}})

;; bot state
(s/def :state/name keyword?)
(s/def :state/handler fn?)
(s/def :state/fail fn?)
(s/def :state/on-enter fn?)
(s/def :state/spec keyword?)
(s/def :state/data map?)
(s/def :state/state (s/keys :req-un [:state/name :state/handler]
                            :opt-un [:state/spec :state/fail :state/data :state/on-enter]))

(defn assoc-bot-state
  [body]
  (when (s/valid? :state/state body)
    (swap! bot-states assoc (:name body) body)))

(comment
  @bot-states)

(defn get-bot-state
  "Gets state by name keyword"
  [name]
  (get @bot-states name))

;; in-memory storage / repository of [user-id]: state
(def state-per-chat-id
  (atom {}))

(comment @state-per-chat-id)

(defn get-state-from-request
  "Get current state associated with user id"
  [request]
  (or (get @state-per-chat-id (-> (get-chat-id request)
                                  str
                                  keyword))
      initial-state))

(defn get-state-data
  [request]
  (:data (get-state-from-request request)))

;; interpreter
(defn get-handler
  "Get handler from current state"
  [request]
  (:handler (get-state-from-request request)))

(defn validate-request
  [request state]
  (if-let [spec (:spec state)]
    (s/valid? spec (get-text request))
    true))

(defn state-handler
  "Tries to retrieve a current state for chat from a store,
  if a state exists in store and has declared :spec, then validate request
  and then call :on-success handler or :on-fail handler"
  ([request]
   (let [current-handler (get-handler request)]
     (if current-handler
       (do
         (log/info "Current handler found " current-handler)
         (if (validate-request request (get-state-from-request request))
           (current-handler request)
           (when-let [fail-handler (:fail (get-state-from-request request))]
             (log/info "Validation failed, but no fail handler found." current-handler)
             (fail-handler request))))
       (do
         (log/info "No current handler found, falling back to default response.")
         (tbot/send-message bot (get-chat-id request) "Неизвестная команда"))))))

(defn assoc-state-with-user
  "associates new state with user id and executes :on-enter side fx, if present"
  [request-or-message new-state]
  (log/info new-state)
  (let [prev-state (get-state-from-request request-or-message)
        new-data (merge (:data prev-state) (:data new-state))]

    (swap! state-per-chat-id assoc (-> (get-chat-id request-or-message)
                                       str
                                       keyword)
           (assoc new-state :data new-data)))
  ;; execute on-enter if present
  (when-let [on-enter (:on-enter new-state)]
    (on-enter request-or-message)))

(defn reset-state-with-user
  "completely resets state associated with user id"
  [request-or-message new-state]
  (swap! state-per-chat-id assoc (-> (get-chat-id request-or-message)
                                     str
                                     keyword) new-state))

;; states specs
(s/def :add-trip/where (s/and string? #(re-matches #"[а-яА-Я\w\s0-9-]+" %)))
(s/def :add-trip/when (s/or :date (s/and string? #(->> (string-to-date %)
                                                       (instance? java.time.LocalDate)))))
(s/def :add-trip/desc string?)

;; actual bot states
(assoc-bot-state
 {:name :add-trip-where
  :on-enter
  (fn [request]
    (tbot/send-message bot (get-chat-id request) "Ок, куда поедем?"))
  :spec :add-trip/where
  :handler (fn [request]
             (tbot/send-message bot (get-chat-id request) "Отлично!")
             (assoc-state-with-user request (-> (get-bot-state :add-trip-when)
                                                (assoc :data {:location (get-text request)}))))
  :fail (fn [request]
          (tbot/send-message bot (get-chat-id request) "Упс, неправильно!"))})

(assoc-bot-state
 {:name :add-trip-when
  :on-enter
  (fn [request]
    (tbot/send-message bot (get-chat-id request) "Скажи, когда ты планируешь поездку? Формат: ДД-ММ-ГГГГ или ДД-ММ-ГГГГ ЧЧ:ММ"))
  :spec :add-trip/when

  :handler (fn [request]
             (tbot/send-message bot (get-chat-id request) "Отлично!")
             (assoc-state-with-user request (-> (get-bot-state :add-trip-description)
                                                (assoc-in [:data :date] (-> (get-text request)
                                                                            string-to-date)))))
  :fail (fn [request]
          (tbot/send-message bot (get-chat-id request) "Неверный формат даты. Формат: ДД-ММ-ГГГГ или ДД-ММ-ГГГГ ЧЧ:ММ"))})

(assoc-bot-state
 {:name :add-trip-description
  :on-enter (fn [request]
              (tbot/send-message bot {:chat_id (get-chat-id request)
                                      :reply_markup {:inline_keyboard
                                                     [[{:text "Пропустить" :callback_data 1}]]}
                                      :text "Напиши дополнительную информацию (можно пропустить)"}))
  :spec :add-trip/desc
  :handler (fn [request]
             (db/add-trip (assoc (get-state-data request)
                                 :user (:id (get-user request)) ;; add user
                                 :description (get-text request))) ;; add description
             (tbot/send-message bot (get-chat-id request) "Супирь! Поездка создана!! 👋")
             (reset-state-with-user request initial-state))})


(comment 
  (defn my-func [{:keys [a b] :or {a 1 b 2}}]
    (println {:a a :b b}))
  (my-func {:b 1})
  )
