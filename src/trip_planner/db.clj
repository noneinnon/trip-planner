(ns trip-planner.db
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [honey.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [trip-planner.helpers :refer [string-to-date]]))

(def db-spec
  {:dbtype   "sqlite"
   :subprotocol "sqlite"
   :dbname     "db/database.sqlite"})

(defn query [db-spec sql-params]
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn sql-params {:return-keys [:total]})))

(comment
  (query db-spec (->
                  {:select [:* [:%count.* :total]]
                   :from [:trips]}
                  (hsql/format {:pretty true}))))

(defn create-db [db-spec]
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["PRAGMA foreign_keys = OFF"])
    (jdbc/execute! conn ["BEGIN TRANSACTION"])
    (jdbc/execute! conn ["CREATE TABLE IF NOT EXISTS users
      (id INTEGER PRIMARY KEY,
      first_name TEXT, 
      username TEXT,
      is_bot BOOLEAN,
      is_premium BOOLEAN,
      language_code TEXT,
      timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)"])

    (jdbc/execute! conn ["CREATE TABLE IF NOT EXISTS trips 
      (id INTEGER PRIMARY KEY,
      description TEXT,
      user INTEGER NOT NULL
        REFERENCES users(id),
      date DATETIME NOT NULL,
      location TEXT
      timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
     )"])
    (jdbc/execute! conn ["COMMIT"])
    (jdbc/execute! conn ["PRAGMA foreign_keys = ON"])))

(s/def :user/id int?)
(s/def :user/first_name string?)
(s/def :user/username string?)
(s/def :user/user (s/keys :req-un [:user/id :user/first_name :user/username]))

(defn save-user [user]
  {:pre [(s/valid? :user/user user)]}
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert! conn :users (s/conform :user/user user))))

(defn get-user [id]
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/get-by-id conn :users id)))

(s/def :trip/user :user/id)
(s/def :trip/description string?)
(s/def :trip/location string?)
(s/def :trip/date #(instance? java.time.LocalDate %))
(s/def :trip/trip (s/keys :req-un [:trip/user :trip/location :trip/date]
                          :opt-un [:trip/description]))

(defn add-trip [trip]
  {:pre [(s/valid? :trip/trip trip)]}
  (log/info "Adding trip" trip)
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert! conn :trips trip)))

(def LIMIT 5)

(defn get-trips-query [{:keys [limit offset where]}]
  (->
   (h/select :* [{:select [[:%count.*]]
                  :from [:trips]
                  :where where} :total])
   (h/from :trips)
   (h/where where)
   (h/join :users [:= :trips.user :users.id])
   (h/limit (or limit LIMIT))
   (h/offset offset)
   (hsql/format {:pretty true})))

(def date-ranges {:today [:between :date [:date "now" "start of day"] [:date "now" "+1 day" "start of day" "-1 second"]]
                  :tomorrow [:between :date [:date "now" "start of day" "+1 day"] [:date "now" "+2 day" "start of day" "-1 second"]]
                  :after-tomorrow [:between :date [:date "now" "start of day" "+2 day"] [:date "now" "+3 day" "start of day" "-1 second"]]
                  :next-week [:between :date [:date "now" "weekday 0" "+1 day"] [:date "now" "weekday 6" "+9 day" "-1 second"]] ;; we count that a week starts from monday
                  :all []})

(defn get-trips [db-spec when? params]
  (query db-spec (get-trips-query (assoc params :where (get date-ranges when?)))))

(comment
  (query db-spec ["select date('now', 'start of day', '+1 day', '-1 second')"])
  (query db-spec ["select date('now')"])
  (s/valid? (s/coll-of #{:one :two}) [ :one ])
  (s/cat (s/map-of #{:one :two} string?) { :one "something" :three 3})
  (get-trips db-spec :next-week {:limit 99 :offset 0})
  (query db-spec (hsql/format {:select [:*]
                               :from [:users]}))

  (query db-spec (->
                  {:select [:* [{:select [[:%count.*]]
                                 :from [:trips]} :total]]
                   :from [:trips]}
                  (hsql/format)))

  (get-trips db-spec :today {:limit 5 :offset 0})

  (add-trip {:description "asd",
             :user 209861504,
             :date (string-to-date "05-07-2023"),
             :location "Unknown"})

  )

