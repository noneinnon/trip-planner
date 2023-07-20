(ns trip-planner.helpers
  (:require
   [clojure.spec.alpha :as s]))

(defn date-to-string [date]
  (->>
   (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")
   (.format date)))

; (defn string-to-date [date-string]
;   (try (-> date-string
;            (java.time.LocalDate/parse (java.time.format.DateTimeFormatter/ofPattern "dd-MM-yyyy")))
;        (catch Exception _ date-string)))

(defn string-to-date
  ([date-string]
   (string-to-date date-string "dd-MM-yyyy"))
  ([date-string format]
   (try (-> date-string
            (java.time.LocalDate/parse (java.time.format.DateTimeFormatter/ofPattern format)))
        (catch Exception _ date-string))))

(defn get-chat-id [request]
  {:pre [(s/valid? :request/update request)]
   :post [(s/valid? int? %)]}
  (or (get-in request [:message :chat :id])
      (get-in request [:chat :id])
      (get-in request [:callback_query :from :id])))

(comment
  (get-chat-id {:message {:chat {:id 1234}}})
  (get-chat-id {:chat {:id 1234}}))

(defn get-user [request]
  {:pre [(s/valid? :request/update request)]
   :post [#(s/valid? :user/user %)]}
  (or (get-in request [:message :from])
      (get-in request [:from])
      (get-in request [:callback_query :from])))

(defn get-text [request]
  (or (get-in request [:message :text])
      (:text request)
      ""))
