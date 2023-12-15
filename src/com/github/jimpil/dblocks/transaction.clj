(ns com.github.jimpil.dblocks.transaction
  (:require [clojure.java.io :as io]
            [next.jdbc :as jdbc]))

(def GET-LOCKS        "SELECT * FROM pg_locks WHERE locktype = 'advisory_xact'")
(def GET-LOCK         (str GET-LOCKS " AND  objid = ?"))
(def ACQUIRE-LOCK     "SELECT pg_advisory_xact_lock(?) AS xact_lock")
(def TRY-ACQUIRE-LOCK "SELECT pg_try_advisory_xact_lock(?) AS xact_lock")
;; no manual releasing here
(defonce TRY-ACQUIRE-LOCK-TIMEOUT
  ;; this is NOT a string, but rather, a memoized function of 1 arg (the db-spec)
  ;; which creates a `plpgsql` FUNCTION, and returns the query string that invokes it.
  (memoize
    (fn [db] ;; will run once (per db)
      (let [qfn (slurp (io/resource "sql/pg_advisory_xact_lock_with_timeout.sql"))]
        (jdbc/execute! db [qfn]) ;; create/install the plpgsql function
        "SELECT __pg_try_advisory_xact_lock_with_timeout__(?, ?) AS lock"))))

(defn acquire-lock!
  [db id]
  (-> db
      (jdbc/execute-one! [ACQUIRE-LOCK id])
      :xact_lock))

(defn try-acquire-lock!
  [db id]
  (-> db
      (jdbc/execute-one! [TRY-ACQUIRE-LOCK id])
      :xact_lock))

(defn try-acquire-lock-with-timeout!
  [db id seconds]
  (-> db
      (jdbc/execute-one! [(TRY-ACQUIRE-LOCK-TIMEOUT db) id seconds])
      :lock))

(defn lock-acquired?
  [db id]
  (-> db
      (jdbc/execute-one! [GET-LOCK id])
      some?))

(defn current-locks
  "Returns all current advisory transaction locks."
  [db]
  (jdbc/execute! db [GET-LOCKS]))

(defmacro with-lock
  [db id & body]
  `(jdbc/with-transaction [tx# ~db]
     (if (acquire-lock! tx# ~id)
       (do ~@body)
       :dblocks/failed-to-acquire)))

(defmacro with-try-lock
  [db id & body]
  `(jdbc/with-transaction [tx# ~db]
     (if (try-acquire-lock! tx# ~id)
       (do ~@body)
       :dblocks/failed-to-acquire)))

(defmacro with-try-lock-timeout
  [db id timeout-sec & body]
  `(jdbc/with-transaction [tx# ~db]
     (if (try-acquire-lock-with-timeout! tx# ~id ~timeout-sec)
       (do ~@body)
       :dblocks/failed-to-acquire)))
