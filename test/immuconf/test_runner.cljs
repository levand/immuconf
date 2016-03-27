(ns immuconf.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [immuconf.config-test]
            [cljs.nodejs :as nodejs]))

(try
  (.install (nodejs/require "source-map-support"))
  (catch :default _))

(doo-tests
 'immuconf.config-test)
