# immuconf

A small library for [managing configuration files 12Factor style](http://12factor.net/config) in Clojure and ClojureScript (nodejs) projects (ported from [the original Clojure-only `levand/immuconf` library](https://github.com/levand/immuconf).)

## Rationale

Every application needs configuration files. There are many different approaches. The purpose of this library is twofold: the first is to codify a set of decisions that have served the original author (Luke VanderHart) well on several projects, and the second is to provide a canonical implementation of the pattern so it doesn't need to be continually re-implemented.

**Characteristics**

- Explicit: configurations are intended to be passed through the code explicitly as function arguments, not globally or ambiently.  No one can *stop* you from binding the config to a global or thread-local var, but that's generally a bad idea anyway.

- Lightweight: as non-intrusive as possible; configurations are normal Clojure/ClojureScript maps.

- Multiplicity: configuration data can be specified at multiple levels, embracing multi-target, multi-environment, multi-user applications.

- Safe: error messages are clear and informative. There are no bare NPEs (under the JVM) or empty `js/Error` exceptions (under nodejs) on missing configuration keys. The application will warn on possibly unintended overrides, or when a configuration is missing keys that it requires.

## Concept

There are different classes of configuration data. Some examples
include (but are not limited to):

- basic configuration used everywhere, usually checked into source control and edited like code;
- private configuration containing sensitive data which cannot be added to source control;
- configuration applying only to specific environments;
- configuration applying only to specific users in a development setting

Immuconf is built around the concept of using a separate configuration file for each of these concerns, each of which are ultimately merged together into a single configuration map. Configuration files are loaded in a constant order. "Upstream" config files can define overridable values which *must* be overridden in a downstream file, or the attempted configuration is invalid. If two config files specify different values, the value in the "downstream" file wins.

As an example, one might specify this sequence of config files:

```bash
["resources/config.edn" "resources/staging.edn" "~/.private/config.edn" "bobdobbs-dev.edn"]
```
- `resources/config.edn` is checked into source control, and defines certain config items that always apply. It also defines _overridable values_. For example, it could specify that there must be a database connection defined in subsequent config files.
- `resources/staging.edn` is also checked into source control. Presumably there are also `resources/prod.edn` and `resources/dev.edn` which could be used instead. This file might specify a database connection, and all the non-sensitive parameters for the database connection: the URI, etc. It might also specify that certain sensitive DB parameters (e.g password) must be present, but would not actually provide them.
- `~/.private/config.edn` would contain the actual sensitive information, such as database passwords, and would not be checked into source control.
- `bobdobbs-dev.edn` could contain developer overrides, for any values, specifically for an individual developer's environments.

If a key is unexpectedly overridden, `immuconf` logs a warning. This helps prevent _accidental_ overrides, while still allowing developers to deliberately override certain values.

### Configuration File Syntax

Configuration files are valid [EDN (Extensible Data Notation)](https://github.com/edn-format/edn) files.

The basic syntax is standard nested maps. Nested collections other than maps are not supported: vectors, sets and list may be terminal "leaf nodes" but they will not be merged when loading multiple config files.

Overrides are implemented using the `immuconf/override` reader literal, and may occur in value position:

```clojure
{:database
  {:uri "jdbc:postgresql://db-server.com:1234/my-database"
   :user "system"
   :password #immuconf/override "Specify the database password here. Ask Ops if you don't have it."}}
```

Values may also specify a *default*, using the `immuconf/default` reader literal. Default values may be overridden without any warning message, but will simply resolve to the given value without a warning if they are *not* overidden.

```clojure
{:environment {:cache-size #immuconf/default 32}}
```

### API

#### Load

The primary api is the `immuconf.config/load` function. It takes any number of arguments. Each argument must be resolvable to a config file, and can be of any type that is a valid argument for `clojure.java.io/reader`. `load` returns a standard Clojure or ClojureScript map, but will throw an error if the config file is invalid (that is, if it contains any values that were specified to have been overridden but were not.)

```clojure
(def my-cfg (immuconf.config/load "resources/config.edn" "resources/dev.edn" "~/.private/config.edn" "bobdobbs-dev.edn"))
```

If `load` is passed *no* arguments, it will first attempt to read an `.immuconf.edn` file (relative to the project path), which should be an EDN file containing a sequence of config file paths.

If there is no `.immuconf.edn` file, it will fall back to reading the value of the `IMMUCONF_CFG` system environment variable, which should be a colon-delimited list of config file paths.

If there is neither an `.immuconf.edn` file nor an `IMMUCONF_CFG`
environment variable, the system will throw an error.

#### Get

The only other public function in the API is `immuconf.config/get`. Its first argument is a config map, any additional arguments are keys (as would be passed to `clojure.core/get-in`.)

```clojure
(immuconf.config/get my-cfg :database :uri)
```

The only differences betwen `immuconf.config/get` and
`clojure.core/get` are that the Immuconf version takes varargs instead of a sequence of keys and also that, if the key is missing, it will throw an info-bearing exception explaining what key was expected but not found, instead of just returning `nil` (which virtually ensures a `NullPointerException` [in the JVM-based implementation] somewhere downstream.)

## Logging

Logging of warnings and errors is an important part of Immuconf, and is implemented logs using `clojure.tools.logging` (in the Clojure environment) and `taoensso.timbre` (in the ClojureScript environment.)

## License

Original work copyright © Luke VanderHart 2015; derivative work (ClojureScript extension) © Russell Whitaker 2016.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
