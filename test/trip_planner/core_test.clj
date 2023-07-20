(ns trip-planner.core-test
  (:require
   [clojure.test :refer :all]
   [honey.sql :as hsql]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [trip-planner.core :refer :all]
   [trip-planner.db :refer [create-db get-trips get-trips-query query]]
   [trip-planner.helpers :refer [date-to-string]]))

(def db-spec
  {:dbtype   "sqlite"
   :subprotocol "sqlite"
   :dbname     "db/database-test.sqlite"})

(defn init []
  (create-db db-spec))

(defn populate [users trips]
  (with-open [conn (jdbc/get-connection db-spec)]
    (sql/insert-multi! conn :users users)
    (sql/insert-multi! conn :trips trips)))

(defn clear-up []
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["delete from users;"])
    (jdbc/execute! conn ["delete from trips;"])))

(defn plus-days [cnt]
  (.plusDays (java.time.LocalDateTime/now) cnt))

(def users [{:id 12345678
             :first_name "TestUser",
             :username "testuser123",
             :timestamp "2023-06-05 13:09:32",
             :language_code "en",
             :is_bot 0,
             :is_premium 1}])

(def trips
  [{:description "today",
    :user 12345678
    :date (plus-days 0),
    :location "Moskva"}
   {:description "tomorrow",
    :user 12345678
    :date (plus-days 1),
    :location "Moskva"}
   {:description "after tomorrow",
    :user 12345678
    :date (plus-days 2),
    :location "Moskva"}
   {:description "next week",
    :user 12345678
    :date (plus-days 10),
    :location "Novosib"}
   {:description "next week",
    :user 12345678
    :date (plus-days 10),
    :location "тольятти"}])

(defn with-data
  [test]
  (populate users trips)
  (test)
  (clear-up))

(use-fixtures :once with-data)

(def opts {:limit 99 :offset 0})

(deftest get-trips-test
  (testing "today should contain one trip"
    (is (= 1 (count (get-trips db-spec :today opts)))))
  (testing (str "all trips should equal to " 5)
    (is (= (count trips) (count (get-trips db-spec :all opts)))))
  (testing "tomorrow should contain one trip"
    (is (= 1 (count (get-trips db-spec :tomorrow opts)))))
  (testing "after tomorrow should contain one trip"
    (is (= 1 (count (get-trips db-spec :after-tomorrow opts)))))
  (testing "next week should contain two trips"
    (is (= 2 (count (get-trips db-spec :next-week opts))))))
