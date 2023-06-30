(ns trip-planner.db
  (:require
   [clojure.spec.alpha :as s]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [trip-planner.helpers :refer [string-to-date]]))

(def db-spec
  {:dbtype   "sqlite"
   :subprotocol "sqlite"
   :dbname     "db/database.sqlite"})

(defn query [sql-params]
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/query conn sql-params)))

(defn create-db []
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["PRAGMA foreign_keys = OFF"])
    (jdbc/execute! conn ["BEGIN TRANSACTION"])
    (jdbc/execute! conn ["CREATE TABLE users
      (id INTEGER PRIMARY KEY,
      first_name TEXT, 
      username TEXT,
      is_bot BOOLEAN,
      is_premium BOOLEAN,
      language_code TEXT,
      timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)"])

    (jdbc/execute! conn ["CREATE TABLE trips 
      (id INTEGER PRIMARY KEY,
      description TEXT,
      user INTEGER NOT NULL
        REFERENCES users(id),
      date DATETIME NOT NULL,
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
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert! conn :trips trip)))

(def LIMIT 5)

(defn get-trips
  ([] (get-trips 0))
  ([offset]
   (with-open [conn (jdbc/get-connection db-spec)]
     (let [query-result
           (sql/query conn [(format "select *, 
      (select count(*) from trips where date >= DATE('now')) as total 
      from trips
      inner join users on users.id = trips.user
      where date >= DATE('now') limit %d offset %d
      ;" LIMIT offset)])
           total (:total (first query-result))]
       {:data query-result :next (+ offset LIMIT)}))))

(comment
  (create-db)
  (get-user 123455123)
  (first (:data (get-trips)))
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["drop table routes;"]))

  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/query conn ["select *, (select count(*) from trips where date >= DATE('now')) as total from trips where date >= DATE('now') limit 2;"]))

  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/get-by-id conn :users 123455123))

  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["alter table trips add column location TEXT"]))

  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert! conn :users {:id 123455123
                              :first_name "anton"
                              :username "noneinnon"}))

  (add-trip {:description "past trip"
             :location "Moskva"
             :date (string-to-date "23-12-2022")})

  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert! conn :trips {:user 123455123
                              :description "past trip"
                              :location "Moskva"
                              :date (string-to-date "23-12-2022")})))
