(ns immuconf.config
  (:refer-clojure :exclude [load get])
  (:require [clojure.walk :as w]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(ns-unmap *ns* 'Override)

(defrecord Override [doc])
(defrecord Default [value])

(defn- override-warning
  "log an override warning, given two config values (as [file value]
  tuples).. Do not log if the value is supposed to be overridden."
  [[base-file base-value] [override-file override-value] path]
  (when-not (or (instance? Override base-value)
                (instance? Default base-value))
    (log/warn "Value at" path
              "in" base-file
              "overridden by value in" override-file)))

(declare merge-cfgs)
(defn- merge-vals
  "Merge two values. Each value is represented as a tuple of [file
  value]."
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
  "Merge a sequence of configuration maps. Each config map is
 represented as a tuple of [file cfg]"
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
  overridden. Also replaces any default value placeholders with the
  actual default value they specify.

  If successful, returns the configuration map. If not, throws an
  error."
  ([cfg-map] (validate-cfg cfg-map []))
  ([value path]
     (cond
      (instance? Override value) (throw (ex-info (format "Config specified that value at %s should have been overridden, but it was not" path)
                                                 {:override value
                                                  :path path}))
      (instance? Default value) (:value value)
      (map? value) (into {} (map (fn [[k v]]
                                   [k (validate-cfg v (conj path k))])
                                 value))
      :else value)))

(defn- load-cfg-file
  "Load a single config file into a config map. Returns a tuple of the
  given filename and the resulting config "
  [file]
  (try
    [file (edn/read-string {:readers *data-readers*} (slurp file))]
    (catch Throwable t
      (throw (ex-info (format "Error loading config file: %s" file)
                      {:file file} t)))))

(declare load)
(defn- load-from-file
  "Attempt to load config files defined in an .immuconf.edn
  file. Return nil if no such file exists."
  []
  (try
    (let [s (slurp ".immuconf.edn")
          cfgs (edn/read-string s)]
      (when-not (empty? cfgs)
        (log/info "Using configuration files specified in .immuconf.edn")
        (apply load cfgs)))
    (catch Exception e
      nil)))

(defn- load-from-env
  "Attempt to load config files from an IMMUCONF_CFG environment
  variable. Return nil if no such variable is defined."
  []
  (let [val (System/getenv "IMMUCONF_CFG")]
    (when-not (empty? val)
      (let [cfgs (s/split val #":")]
        (when-not (empty? cfgs)
          (log/info "Using configuration files specified in $IMMUCONF_CFG")
          (apply load cfgs))))))

(defn- expand-home [filename]
  "Useful on *nix systems to that ~ (home) dirs work"
  (if (.startsWith filename "~")
    (clojure.string/replace-first filename "~" (System/getProperty "user.home"))
    filename))

(defn load
  "If no arguments are specified, attempt to load config files from a
   `.immuconf.edn` file or an IMMUCONF_CFG environment variable, in that
   order.

   If arguments are specified, attempts to load each argument as a
   config file using `clojure.java.io/reader`, and merge them together.

   Returns a valid configuration map.

   If the configuration map specified by the inputs is not valid,
   throws an exception."
  ([] (or
       (load-from-file)
       (load-from-env)
       (throw (ex-info "No configuration files were specified, and neither an .immuconf.edn file nor an IMMUCONF_CFG environment variable was found" {}))))
  ([& cfg-files]
     (validate-cfg
       (merge-cfgs
         (map load-cfg-file
              (map expand-home cfg-files))))))

(defn get
  "Look up the given keys in the specified config. If the key is not
  present, throw an error explaining which key was missing."
  [cfg & keys]
  (let [v (get-in cfg keys ::none)]
    (if (= v ::none)
      (throw (ex-info (format "Attempted to read undefined configuration value %s" keys) {:keys keys}))
      v)))
