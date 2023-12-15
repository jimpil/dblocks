(ns com.github.jimpil.dblocks-test
  (:require [clojure.test :refer :all]
            [com.github.jimpil.dblocks.core :refer :all]
            [com.github.jimpil.dblocks.session :as session]
            [next.jdbc :as jdbc]))

(def DB
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
        ;(Thread/sleep 600)
        (is (= [1 2] @state))))

    (testing "TRY ACQUIRE FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 12345]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= ACQUIRE-FAIL
             (with-session-try-lock DB id (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 600)
        (is (= [1] @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT FROM 2 CONNECTIONS"
      (let [state (volatile! [])
            id 123456]
        (future (with-session-try-lock DB id (println "affecting-1") (Thread/sleep 1500) (vswap! state conj 1)))
        (Thread/sleep 10)
        (is
          (= ACQUIRE-FAIL
             (with-session-try-lock-timeout DB id 1 (println "affecting-2") (vswap! state conj 2)))) ;; no print
        (Thread/sleep 1600)
        (is (= [1] @state))))
    )
  )
