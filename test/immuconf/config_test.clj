(ns immuconf.config-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [immuconf.config :as cfg]))

(deftest basic-merging
  (is (= (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")
         {:a {:b {:c 1 :d 2}}})))

(deftest basic-override
  (is (= (cfg/load "test/fixtures/test-o.edn" "test/fixtures/test-a.edn")
         {:a {:b {:c 1}}})))

(deftest warn-on-unexpected-override
  (let [warning (atom "")]
    (with-redefs [log/log*
                  (fn [_ _ _ msg]
                    (reset! warning msg))]
      (is (= (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-a2.edn")
             {:a {:b {:c 2}}}))
      (is (re-find #"\[:a :b :c\]" @warning)))))

(deftest unsatisfied-override
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"should have been overridden"
         (cfg/load "test/fixtures/test-o.edn"))))

(deftest default-values
  (is (= (cfg/load "test/fixtures/test-d.edn")
         {:a {:b {:c 42}}})))

(deftest overriding-defaults-does-not-warn
  (let [did-warn (atom false)]
    (with-redefs [log/log*
                  (fn [& args]
                    (reset! did-warn true))]
      (is (= (cfg/load "test/fixtures/test-d.edn" "test/fixtures/test-a.edn")
             {:a {:b {:c 1}}}))
      (is (false? @did-warn)))))

(deftest get-basic-value
  (let [cfg (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")]
    (is (= 1 (cfg/get cfg :a :b :c)))))

(deftest friendly-error-on-missing-key
  (let [cfg (cfg/load "test/fixtures/test-a.edn" "test/fixtures/test-b.edn")]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"\(:a :x :c\)"
                          (cfg/get cfg :a :x :c)))))

(deftest path-with-tilde
  (System/setProperty "user.home" (str (System/getProperty "user.dir") "/test"))
  (is (cfg/load "~/fixtures/test-a.edn")))

(deftest missing-files-are-allowed
  (is (= (cfg/load "test/fixtures/test-a.edn"
                   "test/fixtures/this-file-does-not-exist.edn"
                   "test/fixtures/test-b.edn")
         {:a {:b {:c 1 :d 2}}})
      "Missing files are treated as empty maps")
  (is (= {} (cfg/load "test/fixtures/this-file-does-not-exist.edn"
                      "test/fixtures/another-missing-file.edn"))
      "If all files are missing, the config is an empty map"))



