(ns com.github.jimpil.dblocks.core
  (:require [com.github.jimpil.dblocks.session :as session]
            [com.github.jimpil.dblocks.transaction :as transaction]
    ;[com.github.jimpil.dblocks.util        :as util]
            [next.jdbc :as jdbc])
  (:import  [java.util.concurrent ThreadLocalRandom TimeUnit]
            [java.util.concurrent.locks Lock]))

;; Helper protocol for managing user-provided lock ids. 
;; The rules are:
;; 1. Got nil => generate a random one
;; 2. Got integer/long => return it as-is
;; 3. Got string/keyword/symbol  => hash it
;; 4. Got something else => throw `IllegalArgumentException`"
(defprotocol ILockID (id-from [this]))
(extend-protocol ILockID 
  nil  ;; can be dangerous (albeit unlikely)
  (id-from [_] (.nextLong (ThreadLocalRandom/current))) 
  String
  (id-from [this] (hash this))
  clojure.lang.Keyword
  (id-from [this] (hash this))
  clojure.lang.Symbol
  (id-from [this] (hash this))
  Integer 
  (id-from [this] this)
  Long
  (id-from [this] this)
  Object
  (id-from [this] 
    (throw
     (IllegalArgumentException.
      (str "Invalid 'id' type: " (type this)))))
  )

(defmacro with-session-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting if necessary. Releases the lock at the end (explicitly)."
  [db lock-id & body]
  `(let [id# (id-from ~lock-id)]
     (with-open [conn# (jdbc/get-connection ~db)]
       (when (session/acquire-lock! conn# id#)
         (try ~@body
              (finally
                (session/release-lock! conn# id#)))))))

(defmacro with-session-try-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   if available. Releases the lock at the end (explicitly)."
  [db lock-id & body]
  `(let [id# (id-from ~lock-id)]
     (with-open [conn# (jdbc/get-connection ~db)]
       (when (session/try-acquire-lock! conn# id#)
         (try ~@body
              (finally
                (session/release-lock! conn# id#)))))))

(defmacro with-session-try-lock-timeout
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting up to <timeout> seconds. Releases the lock at the end (explicitly)."
  [db lock-id timeout & body]
  `(let [id# (id-from ~lock-id)]
     (with-open [conn# (jdbc/get-connection ~db)]
       (when (session/try-acquire-lock-with-timeout! conn# id# ~timeout)
         (try ~@body
              (finally
                (session/release-lock! conn# id#)))))))


(defmacro with-transation-lock
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), waiting if necessary. Releases the lock at the end 
   of the transaction (implicitly)."
  [db lock-id & body]
  `(transaction/with-lock ~db (id-from ~lock-id) ~@body))

(defmacro with-transation-try-lock
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), if available. Releases the lock at the end 
   of the transaction (implicitly)."
  [db lock-id & body]
  `(transaction/with-try-lock ~db (id-from ~lock-id) ~@body))

(defmacro with-transation-try-lock-timeout
  "Sets up a transaction, and executes <body> inside an exclusive transaction-level 
   advisory lock (per <lock-id>), waiting up to <timeout-seconds> for one. Releases 
   the lock at the end of the transaction (implicitly)."
  [db lock-id timeout-seconds & body]
  `(transaction/with-try-lock-timeout ~db (id-from ~lock-id) ~timeout-seconds ~@body))

(defn pg-session-lock
  "Returns an implementation of `java.util.concurrent.locks.Lock` 
   based on a POSTGRES session advisory lock (per <lock-id>)."
  ^Lock [db lock-id]
  (reify Lock
    (lock              [_] (session/acquire-lock! db (id-from lock-id)))
    (^boolean tryLock  [_] (session/try-acquire-lock! db (id-from lock-id)))
    (^boolean tryLock  [_ ^long n ^TimeUnit unit]
      (session/try-acquire-lock-with-timeout! db (id-from lock-id) (.toSeconds unit n)))
    (unlock            [_] (session/release-lock! db (id-from lock-id)))))
