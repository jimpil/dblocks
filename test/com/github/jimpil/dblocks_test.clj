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
        (Thread/sleep 10) ;; ensure the above future runs before the below
        (with-session-lock DB id (println "affecting-2") (vswap! state conj 2))
        (is (= [1 2] @state))))

    (testing "TRY ACQUIRE FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 1234]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= :dblocks/failed-to-acquire
             (with-session-try-lock DB id (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 600)
        (is (= [1] @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 12345]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 1500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= :dblocks/failed-to-acquire
             (with-session-try-lock-timeout DB id 1 (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 1600)
        (is (= [1] @state))))
    )
  )

(deftest transaction-lock-tests
  (testing "TRANSACTION LOCK"
    (testing "ACQUIRE FROM 2 TRANSACTIONS"
      (let [state (volatile! [])
            id 123]
        (future (with-transation-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (with-session-lock DB id (println "affecting-2") (vswap! state conj 2))
        (is (= [1 2] @state))))

    (testing "TRY ACQUIRE FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 1234]
        (future (with-transation-try-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= :dblocks/failed-to-acquire
             (with-transation-try-lock DB id (println "affecting-2") (vswap! state conj 2))))
        (Thread/sleep 600)
        (is (= [1] @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 12345]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 1500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= :dblocks/failed-to-acquire
             (with-transation-try-lock-timeout DB id 1 (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 1600)
        (is (= [1] @state))))

    )
  )
