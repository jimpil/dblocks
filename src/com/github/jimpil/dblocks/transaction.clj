(ns com.github.jimpil.dblocks.transaction
  (:require [next.jdbc :as jdbc]))

(def GET-LOCKS        "SELECT * FROM pg_locks WHERE locktype = 'advisory_xact'")
(def GET-LOCK         (str GET-LOCKS " AND  objid = ?"))
(def ACQUIRE-LOCK     "SELECT pg_advisory_xact_lock(?) AS xact_lock")
(def TRY-ACQUIRE-LOCK "SELECT pg_try_advisory_xact_lock(?) AS xact_lock")
;; no manual releasing here

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
  `(jdbc/with-transaction [conn# ~db]
     (if (acquire-lock! conn# ~id)
       (do ~@body)
       :dblocks/failed-to-acquire)))

(defmacro with-try-lock
  [db id & body]
  `(jdbc/with-transaction [conn# ~db]
     (if (try-acquire-lock! conn# ~id)
       (do ~@body)
       :dblocks/failed-to-acquire)))

(defmacro with-try-lock-timeout
  [db id timeout-sec & body]
  `(jdbc/with-transaction [conn# ~db]
     (jdbc/execute-one! conn# ["SELECT set_config('lock_timeout', ?, FALSE);" (str ~timeout-sec \s)])
     (if (try-acquire-lock! conn# ~id)
       (do ~@body)
       :dblocks/failed-to-acquire)))
