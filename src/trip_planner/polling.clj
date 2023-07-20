(ns trip-planner.polling
  (:require
   [clojure.tools.logging :as log]
   [telegrambot-lib.core :as tbot]
   [trip-planner.core :refer [new-handler]]
   [trip-planner.helpers :refer [get-chat-id]]))

(def config
  {:timeout 10
   :sleep 5000})

(defn poll-updates
  "Long poll for recent chat messages from Telegram."
  ([bot]
   (poll-updates bot nil))

  ([bot offset]
   (let [resp (tbot/get-updates bot {:offset offset
                                     :timeout (:timeout config)})]
     (if (contains? resp :error)
       (log/error "tbot/get-updates error:" (:error resp))
       resp))))

(defonce update-id (atom nil))

(defn set-id!
  "Sets the update id to process next as the the passed in `id`."
  [id]
  (reset! update-id id))

;; this is a long-polling example
;; for webhook example refer to https://github.com/wdhowe/telegrambot-lib/wiki/Getting-and-Sending-Messages

(defn app
  "Retrieve and process chat messages."
  [bot]
  (log/info "bot service started.")

  (future (loop []
            (log/debug "checking for chat updates.")
            (let [updates (poll-updates bot @update-id)
                  messages (:result updates)]
              ; (log/info "Current updates: " updates)

      ;; Check all messages, if any, for commands/keywords.
              (doseq [msg messages]
        ;(some-handle-msg-fn bot msg) ; your fn that decides what to do with each message.
                (try (new-handler bot msg)
                     (catch Exception e (do (log/warn "Exception occured while handling request" e)
                                            (tbot/send-message bot (get-chat-id msg) "Упс, что-то пошло не так!"))))
        ;; Increment the next update-id to process.
                (-> msg
                    :update_id
                    inc
                    set-id!))

      ;; Wait a while before checking for updates again.
              (Thread/sleep (:sleep config)))
            (recur))))



