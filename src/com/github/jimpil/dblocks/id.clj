(ns com.github.jimpil.dblocks.id
  (:import (clojure.lang Keyword Symbol)
           (java.util.concurrent ThreadLocalRandom)))

;; Helper protocol for managing user-provided lock ids.
;; The rules are:
;; 1. Got nil => generate a random one
;; 2. Got integer/long => return it as-is
;; 3. Got string/keyword/symbol  => hash it
;; 4. Got something else => throw `IllegalArgumentException`"
(defprotocol ILockID (from [this]))
(extend-protocol ILockID
  nil  ;; can be dangerous (albeit unlikely)
  (from [_] (.nextLong (ThreadLocalRandom/current)))
  String
  (from [this] (hash this))
  Keyword
  (from [this] (hash this))
  Symbol
  (from [this] (hash this))
  Integer
  (from [this] this)
  Long
  (from [this] this)
  Object
  (from [this]
    (throw
      (IllegalArgumentException.
        (str "Invalid 'id' type: " (type this)))))
  )
