(ns com.github.jimpil.dblocks.session
  (:require [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [com.github.jimpil.dblocks.id :as id])
  (:import (java.sql Connection)))

(def GET-LOCKS        "SELECT * FROM pg_locks WHERE locktype = 'advisory'")
(def GET-LOCK         (str GET-LOCKS " AND  objid = ?"))
(def ACQUIRE-LOCK     "SELECT pg_advisory_lock(?) AS lock")
(def TRY-ACQUIRE-LOCK "SELECT pg_try_advisory_lock(?) AS lock")
(def RELEASE-LOCK     "SELECT pg_advisory_unlock(?) AS unlock")
(def RELEASE-LOCKS    "SELECT pg_advisory_unlock_all()") ;; is implicitly invoked at session end, even if the client disconnects ungracefully

(defonce TRY-ACQUIRE-LOCK-TIMEOUT
  ;; this is NOT a string, but rather, a memoized function of 1 arg (the db-spec)
  ;; which creates a `plpgsql` FUNCTION, and returns the query string that invokes it.
  (memoize
   (fn [db] ;; will run once (per db)
     (let [qfn (slurp (io/resource "sql/pg_advisory_lock_with_timeout.sql"))]
       (jdbc/execute! db [qfn]) ;; create/install the plpgsql function
       "SELECT __pg_try_advisory_lock_with_timeout__(?, ?) AS lock"))))

(defn acquire-lock!
  [db id]
  (-> db
      (jdbc/execute-one! [ACQUIRE-LOCK id])
      :lock))

(defn try-acquire-lock!
  [db id]
  (-> db
      (jdbc/execute-one! [TRY-ACQUIRE-LOCK id])
      :lock))

(defn try-acquire-lock-with-timeout!
  [db id seconds]
  (-> db
      (jdbc/execute-one! [(TRY-ACQUIRE-LOCK-TIMEOUT db) id seconds])
      :lock))

(defn release-lock!
  "Releases a specific advisory lock (per <id>)."
  [db id]
  (-> db
      (jdbc/execute-one! [RELEASE-LOCK id])
      :unlock))

(defn lock-acquired?
  [db id]
  (-> db
      (jdbc/execute-one! [GET-LOCK id])
      some?))

(defn release-locks!
  "Releases all advisory locks currently held."
  [db]
  (jdbc/execute-one! db [RELEASE-LOCKS]))

(defn current-locks
  "Returns all current advisory session locks."
  [db]
  (jdbc/execute! db [GET-LOCKS]))

(extend-protocol next.jdbc.protocols/Connectable
  Connection
  (get-connection [this _] this))

(defn lock*
  "Helper for correctly using one of the three
   'acquire' variants above."
  ([acquire! db id f]
   (lock* acquire! db id nil f))
  ([acquire! db id timeout f]
   (let [id   (id/from id)
         conn (jdbc/get-connection db)
         acquired? (if (nil? timeout)
                     (acquire! conn id)
                     (acquire! conn id timeout))]
     (if acquired?
       (try (f)
            (finally
              (if (instance? Connection db)
                ;; we are enclosed in an outer session (i.e. Connection)
                ;; don't touch it - just release the lock
                (release-lock! conn id)
                ;; we are in the session we ourselves opened
                ;; we can close it (will release all locks)
                (.close conn))))
       :dblocks/failed-to-acquire))))
