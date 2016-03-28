(ns immuconf.config
  "Main namespace for Immuconf functionality"
  (:refer-clojure :exclude [load get])
  (:require [clojure.walk :as w]
            [clojure.string :as s]
            #?(:clj [environ.core :refer [env]])
            #?(:clj  [clojure.tools.logging :as log]
               :cljs [taoensso.timbre :as log :refer-macros [warn info]])
            #?(:cljs [cljs.pprint :refer [cl-format]])
            #?(:cljs [cljs.nodejs :as nodejs])
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :refer [read-string register-tag-parser!]])))

#?(:clj (ns-unmap *ns* 'Override))

(defrecord Override [doc])
(defrecord Default [value])
#?(:cljs (register-tag-parser! 'immuconf/override immuconf.config/->Override))
#?(:cljs (register-tag-parser! 'immuconf/default immuconf.config/->Default))

(defn logger
  "Wrapper entrypoint for both clj and cljs logging facilities
  "
  [facility & args]
  (case facility
    :warn (log/warn args)
    :info (log/info args)))

(defn- override-warning
  "Log an override warning, given two config values (as [file value]
   tuples). Do not log if the value is supposed to be overridden.
  "
  [[base-file base-value] [override-file override-value] path]
  (when-not (or (instance? Override base-value)
                (instance? Default base-value))
    (logger :warn "Value at" path
            "in" base-file
            "overridden by value in" override-file)))

(declare merge-cfgs)
(defn- merge-vals
  "Merge two values, each represented as a tuple of [file value]
  "
  [values path]
  (cond
   ;; When equal, use any of the values with no warning
   (apply = (map second values)) (second (last values))
   ;; when they are all maps, merge!
   (every? (comp map? second) values) (merge-cfgs values path)
   ;; Otherwise, warn on override and return the last one
   :else (do (override-warning (first values) (last values) path)
             (second (last values)))))

(defn- merge-cfgs
  "Merge a sequence of configuration maps, each
   represented as a tuple of [file cfg]
  "
  ([cfgs] (merge-cfgs cfgs []))
  ([cfgs path]
     (let [files (map first cfgs)
           maps (map second cfgs)
           keys (into #{} (mapcat keys maps))]
       (into {}
             (for [k keys]
               (let [vals (map #(clojure.core/get % k ::none) maps)
                     val-tuples (map vector files vals)
                     actual-vals (filter #(not= ::none (second %)) val-tuples)]
                 [k (merge-vals actual-vals (conj path k))]))))))

(defn- validate-cfg
  "Validate that a configuration map has no overrides that were not
   overridden; also replace any default value placeholders with the
   actual specified default value.

   If successful, returns configuration map; if not, throws an error.
  "
  ([cfg-map] (validate-cfg cfg-map []))
  ([value path]
     (cond
      (instance? Override value)
      (throw (ex-info
               #?(:clj (format "Config specified that value at %s should have been overridden, but it was not" path)
                  :cljs (cl-format nil "Config specified that value at ~a should have been overridden, but it was not" path))
               {:override value :path path}))
      (instance? Default value) (:value value)
      (map? value) (into {} (map (fn [[k v]]
                                   [k (validate-cfg v (conj path k))])
                                 value))
      :else value)))

(defn- load-cfg-file
  "Load a single config file into a config map.
   Return a tuple of the given filename and the resulting config.
  "
  [file]
  (try
    [file #?(:clj (edn/read-string {:readers *data-readers*} (slurp file))
             :cljs (-> (nodejs/require "fs")
                       (.readFileSync file "UTF-8")
                       read-string))]
    (catch #?(:clj Throwable :cljs js/Error) t
      (throw (ex-info #?(:clj (format "Error loading config file: %s" file)
                         :cljs (cl-format nil "Error loading config file: ~a" file))
                      {:file file} t)))))

(declare load)
(defn- load-from-file
  "Attempt to load config files defined in an .immuconf.edn
   file. Return nil if no such file exists.
  "
  []
  (try
    (let [s #?(:clj (slurp ".immuconf.edn")
               :cljs (-> (nodejs/require "fs")
                         (.readFileSync ".immuconf.edn" "UTF-8")
                         read-string))
          cfgs #?(:clj (edn/read-string s)
                  :cljs (read-string s))]
      (when-not (empty? cfgs)
        (logger :info "Using configuration files specified in .immuconf.edn")
        (apply load cfgs)))
    (catch #?(:clj Exception :cljs js/Error) e
      nil)))

(defn- load-from-env
  "Attempt to load config files from an IMMUCONF_CFG environment
   variable. Return nil if no such variable is defined.
  "
  []
  (let [val #?(:cljs nodejs/process.env.IMMUCONF_CFG :clj (:immuconf-cfg env))]
    (when-not (empty? val)
      (let [cfgs (s/split val #":")]
        (when-not (empty? cfgs)
          (logger :info "Using configuration files specified in $IMMUCONF_CFG")
          (apply load cfgs))))))

(defn load
  "If no arguments are specified, attempt to load config files from an
   `.immuconf.edn` file or an IMMUCONF_CFG environment variable, in that
   order.

   If arguments are specified, attempts to load each argument as a
   config file using `clojure.java.io/reader`, and merge them together.

   Returns a valid configuration map.

   If the configuration map specified by the inputs is not valid,
   throws an exception.
  "
  ([] (or
       (load-from-file)
       (load-from-env)
       (throw (ex-info "No configuration files were specified, and neither an .immuconf.edn file nor an IMMUCONF_CFG environment variable was found" {}))))
  ([& cfg-files]
     (validate-cfg (merge-cfgs (map load-cfg-file cfg-files)))))

(defn get
  "Look up the given keys in the specified config. If a key is not
   present, throw an error explaining which key was missing.
  "
  [cfg & keys]
  (let [v (get-in cfg keys ::none)]
    (if (= v ::none)
      (throw
        (ex-info #?(:clj (format "Attempted to read undefined configuration value %s" keys)
                    :cljs (cl-format nil "Attempted to read undefined configuration value ~a" keys))
                 {:keys keys}))
      v)))
