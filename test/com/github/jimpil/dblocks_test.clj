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
    (testing "ACQUIRE"
      (let [state (volatile! 0)
            id 123]
        (future (with-session-lock DB id (println "affecting1") (Thread/sleep 500) (vswap! state inc)))
        ;(Thread/sleep 100)
        (future (with-session-lock DB id (println "affecting2") (vswap! state inc)))
        (Thread/sleep 600)
        (is (== 2 @state))))

    (testing "TRY ACQUIRE"
      (let [state (volatile! 0)
            id 12345]
        (future (with-session-try-lock DB id (println "affecting1") (Thread/sleep 500) (vswap! state inc)))
        ;(Thread/sleep 100)
        (future (with-session-try-lock DB id (println "affecting2") (vswap! state inc)))
        (Thread/sleep 600)
        (is (== 1 @state))))

    (testing "TRY ACQUIRE WITH TIMEOUT"
      (let [state (volatile! 0)
            id 123456]
        (future (with-session-try-lock DB id (println "affecting1") (Thread/sleep 1500) (vswap! state inc)))
        ;(Thread/sleep 100)
        (future (with-session-try-lock-timeout DB id 1 (println "affecting2") (vswap! state inc)))
        (Thread/sleep 1600)
        (is (== 1 @state))))
    )
  )
