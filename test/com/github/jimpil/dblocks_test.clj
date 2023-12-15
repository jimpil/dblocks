(ns com.github.jimpil.dblocks-test
  (:require [clojure.test :refer :all]
            [com.github.jimpil.dblocks.core :refer :all]))

(def DB ;; see docker-compose.yml
  {:dbtype "postgres"
   :dbname "dblocks"
   :user   "root"
   :password "secret"})

(deftest session-locks-tests
  (testing "SESSION LOCK"
    (testing "ACQUIRE FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 123]
        (future (with-session-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (with-session-lock DB id (println "affecting-2") (vswap! state conj 2))
        (is (= [1 2] @state))))

    (testing "TRY ACQUIRE FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 1234]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (is
          (= :dblocks/failed-to-acquire
             (with-session-try-lock DB id (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 600)
        (is (= [1] @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 12345]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 1500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (is
          (= :dblocks/failed-to-acquire
             (with-session-try-lock-timeout DB id 1 (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 600)
        (is (= [1] @state))))
    )
  )

(deftest transaction-lock-tests
  (testing "TRANSACTION LOCK"
    (testing "ACQUIRE FROM 2 TRANSACTIONS"
      (let [state (volatile! [])
            id 123]
        (future (with-transaction-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (with-transaction-lock DB id (println "affecting-2") (vswap! state conj 2))
        (is (= [1 2] @state))))

    (testing "TRY ACQUIRE FROM 2 TRANSACTIONS"
      (let [state (volatile! [])
            id 1234]
        (future (with-transaction-try-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (is
          (= :dblocks/failed-to-acquire
             (with-transaction-try-lock DB id (println "affecting-2") (vswap! state conj 2))))
        (Thread/sleep 600)
        (is (= [1] @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT FROM 2 TRANSACTIONS"
      (let [state (volatile! [])
            id 12345]
        (future (with-transaction-try-lock DB id (println "affecting-1") (Thread/sleep 1500) (vswap! state conj 1)))
        (Thread/sleep 50) ;; give the future a chance to start
        (is
          (= :dblocks/failed-to-acquire
             (with-transaction-try-lock-timeout DB id 1 (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 600)
        (is (= [1] @state))))

    )
  )
