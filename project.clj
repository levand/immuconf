(defproject russellwhitaker/immuconf "0.2.2"
  :description "Manage config files 12Factor style in Clojure/ClojureScript projects"
  :url "http://github.com/russellwhitaker/immuconf"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.34"]
                 [org.clojure/tools.logging "0.3.1"]
                 [environ             "1.0.2"]
                 [com.taoensso/timbre "4.3.1"]]
  :plugins [[lein-cljsbuild              "1.1.3"]
            [lein-npm                    "0.6.0"]
            [lein-doo                    "0.1.7-SNAPSHOT"]
            [lein-cljfmt                 "0.4.1"]
            [lein-ancient                "0.6.8"]
            [jonase/eastwood             "0.2.3"]]
  :npm {:dependencies [[source-map-support "0.4.0"]]}
  :cljsbuild
    {:builds [{:id "immuconf"
               :source-paths ["src"]
               :compiler {:output-to     "target/immuconf/config.js"
                          :output-dir    "target/immuconf"
                          :source-map    "target/immuconf/config.js.map"
                          :target        :nodejs
                          :language-in   :ecmascript5
                          :optimizations :advanced}}
              {:id "immuconf-test"
               :source-paths ["src" "test"]
               :compiler {:output-to     "target/immuconf-test/config.js"
                          :output-dir    "target/immuconf-test"
                          :target        :nodejs
                          :language-in   :ecmascript5
                          :optimizations :none
                          :main          immuconf.test-runner}}]})
