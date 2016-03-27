(ns immuconf.config-test
  "Main test suite for immuconf.config"
  (:require #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is]])
            #?(:clj  [clojure.tools.logging :as log])
                     [immuconf.config :as cfg]))

(deftest basic-merging
  (is (= (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")
         {:a {:b {:c 1 :d 2}}})))

(deftest basic-override
  (is (= (cfg/load "test/fixtures/test-o.edn" "test/fixtures/test-a.edn")
         {:a {:b {:c 1}}})))

(deftest warn-on-unexpected-override
  (let [warning (atom "")]
    (with-redefs #?(:clj  [log/log* (fn [_ _ _ msg] (reset! warning msg))]
                    :cljs [cfg/logger (fn [_ _ msg] (reset! warning (str msg)))])
      (is (= (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-a2.edn")
             {:a {:b {:c 2}}}))
      (is (re-find #"\[:a :b :c\]" @warning)))))

(deftest unsatisfied-override
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                           :cljs js/Error) #"should have been overridden"
                        (cfg/load "test/fixtures/test-o.edn"))))

(deftest default-values
  (is (= (cfg/load "test/fixtures/test-d.edn")
         {:a {:b {:c 42}}})))

(deftest overriding-defaults-does-not-warn
  (let [did-warn (atom false)]
    (with-redefs [#?(:clj log/log*
                     :cljs cfg/logger) (fn [& args] (reset! did-warn true))]
      (is (= (cfg/load "test/fixtures/test-d.edn" "test/fixtures/test-a.edn")
             {:a {:b {:c 1}}}))
      (is (false? @did-warn)))))

(deftest get-basic-value
  (let [cfg (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")]
    (is (= 1 (cfg/get cfg :a :b :c)))))

(deftest friendly-error-on-missing-key
  (let [cfg (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")]
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs js/Error)
                          #"\(:a :x :c\)"
                          (cfg/get cfg :a :x :c)))))
