(ns trip-planner.state
  (:require
   [clojure.spec.alpha :as s]
   [morse.api :as t]
   [trip-planner.config :refer [token]]
   [clojure.tools.logging :as log]
   [trip-planner.db :as db]
   [trip-planner.helpers :refer [get-chat-id get-text get-user string-to-date]]))

(def ^{:private true} bot-states (atom {}))

;;
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

(defmacro bot-handler
  [bindings & body]
  `(fn [~bindings] ~@body))

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

;; in-memory state
(def state-per-user-id
  (atom {}))

(comment @state-per-user-id)

(defn get-state-from-request
  "Get current state associated with user id"
  [request]
  (or (get @state-per-user-id (-> (get-chat-id request)
                                  str
                                  keyword))
      initial-state))

(defn get-state-data
  [request]
  (:data (get-state-from-request request)))

(defn assoc-state-with-user
  "associates new state with user id"
  [request-or-message new-state]
  (log/info new-state)
  (let [prev-state (get-state-from-request request-or-message)
        new-data (merge (:data prev-state) (:data new-state))]

    (swap! state-per-user-id assoc (-> (get-chat-id request-or-message)
                                       str
                                       keyword)
           (assoc new-state :data new-data)))
  ;; execute on-enter if present
  (when-let [on-enter (:on-enter new-state)]
    (on-enter request-or-message)))

(defn reset-state-with-user
  "completely resets state associated with user id"
  [request-or-message new-state]
  (swap! state-per-user-id assoc (-> (get-chat-id request-or-message)
                                     str
                                     keyword) new-state))

(defn get-handler
  "Get handler from current state"
  [request]
  (:handler (get-state-from-request request)))

;; states specs
(s/def :add-trip/where (s/and string? #(re-matches #"[а-яА-Я\w\s0-9]+" %)))
(s/def :add-trip/when (s/or :date (s/and string? #(->> (string-to-date %)
                                                       (instance? java.time.LocalDate)))))
(s/def :add-trip/desc string?)

(comment
  (s/conform :add-trip/when "28-02-2024"))

;; actual bot states
(assoc-bot-state {:name :add-trip-where
                  :on-enter (bot-handler request
                                         (t/send-text token (get-chat-id request) "Ок, куда поедем?"))
                  :spec :add-trip/where
                  :handler (bot-handler request
                                        (t/send-text token (get-chat-id request) "Отлично!")
                                        (assoc-state-with-user request (-> (get-bot-state :add-trip-when)
                                                                           (assoc :data {:location (get-text request)}))))
                  :fail (bot-handler request
                                     (t/send-text token (get-chat-id request) "Упс, неправильно!"))})

(assoc-bot-state {:name :add-trip-when
                  :on-enter
                  (bot-handler request
                               (t/send-text token (get-chat-id request) "Скажи, когда ты планируешь поездку? Формат: ДД-ММ-ГГГГ или ДД-ММ-ГГГГ ЧЧ:ММ"))
                  :spec :add-trip/when

                  :handler (bot-handler request
                                        (t/send-text token (get-chat-id request) "Отлично!")
                                        (assoc-state-with-user request (-> (get-bot-state :add-trip-description)
                                                                           (assoc-in [:data :date] (-> (get-text request)
                                                                                                       string-to-date)))))
                  :fail (bot-handler request
                                     (t/send-text token (get-chat-id request) "Упс, неправильно!"))})

(assoc-bot-state
 {:name :add-trip-description
  :on-enter (bot-handler request
                         (t/send-text token (get-chat-id request) "Напиши дополнительную информацию (можно пропустить)"))
  :spec :add-trip/desc
  :handler (bot-handler request
                        (db/add-trip (assoc (get-state-data request)
                                            :user (:id (get-user request)) ;; add user
                                            :description (get-text request))) ;; add description
                        (t/send-text token (get-chat-id request) "Супирь! Поездка создана!! 👋")
                        (reset-state-with-user request initial-state))})