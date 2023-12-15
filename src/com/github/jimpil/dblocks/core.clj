(ns com.github.jimpil.dblocks.core
  (:require [com.github.jimpil.dblocks
             [id :as id]
             [session :as session]
             [transaction :as transaction]])
  (:import [java.util.concurrent TimeUnit]
           [java.util.concurrent.locks Lock]))

;---------------<SESSION SCOPED>----------------
(defmacro with-session-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting if necessary. Releases the lock at the end, either explicitly (if already
   in a session), or by closing the session we opened. Returns whatever <body> returns."
  [db lock-id & body]
  `(session/lock* session/acquire-lock! ~db ~lock-id (fn [] ~@body)))

(defmacro with-session-try-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   if available. Releases the lock at the end, either explicitly (if already in a session),
   or by closing the session we opened. Returns whatever <body> returns if the lock is
   successfully acquired, otherwise returns `:dblocks/failed-to-acquire`."
  [db lock-id & body]
  `(session/lock* session/try-acquire-lock! ~db ~lock-id (fn [] ~@body)))

(defmacro with-session-try-lock-timeout
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting up to <timeout> seconds. Releases the lock at the end, either explicitly
   (if already in a session), or by closing the session we opened. Returns whatever <body>
   returns if the lock is successfully acquired, otherwise returns `:dblocks/failed-to-acquire`."
  [db lock-id timeout & body]
  `(session/lock* session/try-acquire-lock-with-timeout! ~db ~lock-id ~timeout (fn [] ~@body)))

;---------------<TRANSACTION SCOPED>----------------
(defmacro with-transaction-lock
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), waiting if necessary. Releases the lock at the end 
   of the transaction (implicitly)."
  [db lock-id & body]
  `(transaction/with-lock ~db (id/from ~lock-id) ~@body))

(defmacro with-transaction-try-lock
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), if available. Releases the lock at the end 
   of the transaction (implicitly)."
  [db lock-id & body]
  `(transaction/with-try-lock ~db (id/from ~lock-id) ~@body))

(defmacro with-transaction-try-lock-timeout
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), waiting up to <timeout-seconds> for one. Releases 
   the lock at the end of the transaction (implicitly)."
  [db lock-id timeout-seconds & body]
  `(transaction/with-try-lock-timeout ~db (id/from ~lock-id) ~timeout-seconds ~@body))

(defn pg-session-lock
  "Returns an implementation of `java.util.concurrent.locks.Lock` 
   based on a POSTGRES session advisory lock (per <lock-id>)."
  ^Lock [db lock-id]
  (reify Lock
    (lock              [_] (session/acquire-lock! db (id/from lock-id)))
    (^boolean tryLock  [_] (session/try-acquire-lock! db (id/from lock-id)))
    (^boolean tryLock  [_ ^long n ^TimeUnit unit]
      (session/try-acquire-lock-with-timeout! db (id/from lock-id) (.toSeconds unit n)))
    (unlock            [_] (session/release-lock! db (id/from lock-id)))))
