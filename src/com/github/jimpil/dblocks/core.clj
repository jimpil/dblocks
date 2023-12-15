(ns com.github.jimpil.dblocks.core
  (:require [com.github.jimpil.dblocks.session :as session]
            [com.github.jimpil.dblocks.transaction :as transaction]
    ;[com.github.jimpil.dblocks.util        :as util]
            [next.jdbc :as jdbc])
  (:import [java.util.concurrent ThreadLocalRandom TimeUnit]
           [java.util.concurrent.locks Lock]))

(def ACQUIRE-FAIL :dblocks/failed-to-acquire)

(def connection? (partial instance? java.sql.Connection))

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

(extend-protocol next.jdbc.protocols/Connectable
  java.sql.Connection
  (get-connection [this _] this))

(defn session-lock*
  "Helper for removing the boilerplate from the session macros."
  ([acquire! db id f]
   (session-lock* acquire! db id nil f))
  ([acquire! db id timeout f]
   (let [id   (id-from id)
         conn (jdbc/get-connection db)
         acquired? (if (nil? timeout)
                     (acquire! conn id)
                     (acquire! conn id timeout))]
     (if acquired?
       (try (f)
            (finally
              (if (connection? db)
                ;; we are enclosed in an outer session (i.e. Connection)
                ;; don't touch it - just release the lock
                (session/release-lock! conn id)
                ;; we are in the session we ourselves opened
                ;; we can close it (will release all locks)
                (.close conn))))
       ACQUIRE-FAIL))))

(defmacro with-session-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting if necessary. Releases the lock at the end, either explicitly (if already
   in a session), or by closing the session we opened. Returns whatever <body> returns."
  [db lock-id & body]
  `(session-lock* session/acquire-lock! ~db ~lock-id (fn [] ~@body)))

(defmacro with-session-try-lock
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   if available. Releases the lock at the end, either explicitly (if already in a session),
   or by closing the session we opened. Returns whatever <body> returns if the lock is
   successfully acquired, otherwise returns `:dblocks/failed-to-acquire`."
  [db lock-id & body]
  `(session-lock* session/try-acquire-lock! ~db ~lock-id (fn [] ~@body)))

(defmacro with-session-try-lock-timeout
  "Executes <body> inside an exclusive session-level advisory lock (per <lock-id>), 
   waiting up to <timeout> seconds. Releases the lock at the end, either explicitly
   (if already in a session), or by closing the session we opened. Returns whatever <body>
   returns if the lock is successfully acquired, otherwise returns `:dblocks/failed-to-acquire`."
  [db lock-id timeout & body]
  `(session-lock* session/try-acquire-lock-with-timeout! ~db ~lock-id ~timeout (fn [] ~@body)))
;;-----------------------------------------------------------------------------------------------
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
