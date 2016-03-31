# immuconf

A small library for explicitly managing configuration files in a
Clojure program.

## Installation

[![Clojars Project](http://clojars.org/levand/immuconf/latest-version.svg)](http://clojars.org/levand/immuconf)

## Rationale

Every application needs configuration files. There are many different
approaches. The purpose of this library is twofold: the first is to
codify a set of decisions that have served me well on several
projects, and the second to provide a canonical implementation of the
pattern so it doesn't need to be continually re-implemented.

**Characteristics:**

- Explicit. Configurations are intended to be passed through the code
  explicitly as function arguments, not globally or ambiently. Note
  that I can't *stop* you from binding the config to a global or
  thread-local var but from personal experience its a bad idea.

- Lightweight. As non-intrusive as possible. Configs are normal
  Clojure maps.

- Multiplicity. Configuration data can be specified at multiple
  levels, embracing multi-target, multi-environment, multi-user
  applications.

- Safe. Error messages are clear and informative. There are no bare
  null pointer exceptions on missing configuration keys. The
  application will warn on possibly unintended overrides, or when a
  configuration is missing keys that it requires.

## Concept

There are different classes of configuration data. Some examples
include (but are not limited to):

- Basic configuration that is used everywhere. Usually it is checked
  into source control and edited like code.
- Private configuration that contains sensitive data which cannot be
  added to source control.
- Configuration that applies only to specific environments.
- Configuration that applies only to specific users in a development
  setting.

Immuconf is built around the concept of using a seperate configuration
file for each of these concerns, which are ultimately merged together
into a single configuration map. Configuration files are loaded in a
constant order. "Upstream" config files can define overridable values
which *must* be overridden in a downstream file, or the configuration
is invalid. If two config files specify different values, the value in
the "downstream" file wins.

As an example, one might specify this sequence of config files:

    ["resources/config.edn" "resources/staging.edn" "~/.private/config.edn" "luke-dev.edn"]

- `resources/config.edn` is checked into git. It defines certain
  config items that always apply. It also defines _overridable
  values_. For example, it could specify that there must be a database
  connection defined in subsequent config files.
- `resources/staging.edn` is also checked into git. Presumably there
  are also `resources/prod.edn` and `resources/dev.edn` which could be
  used instead. This file might specify a database connection, and all
  the non-sensitive parameters for the database connection: the URI,
  etc. It would also specify that certain sensitive DB parameters
  (e.g, the password) must be present, but would not actually provide them.
- `~/.private/config.edn` would contain the actual sensitive
  information, such as database passwords, and would not be checked
  into git.
- `luke-dev.edn` could contain developer overrides, for any values,
  specifically for an individual developer's environments.

If a key is unexpectedly overridden, it logs a warning. This helps
prevent accidental overrides, but allows developers to deliberately
override certain values.

### Config File Syntax

Config files are valid EDN files.

The basic syntax is standard nested maps. Nested collections other than
maps are not supported - vectors, sets and list may be terminal
"leaves" but they will not be merged when loading multiple config
files.

Overrides are implemented via the `immuconf/override` reader literal,
and may occur in value position:

```clojure
{:database {:uri "jdbc:postgresql://db-server.com:1234/my-database"
            :user "system"
            :password #immuconf/override "Specify the database password here.
                                          Ask Jim if you don't have it."}}
```

Values may also specify a *default*, using the `immuconf/default`
reader literal. Default values may be overridden without any warning
message, but will simply resolve to the given value without a warning
if they are *not* overidden.

```clojure
{:environment {:cache-size #immuconf/default 32}}
```

### API

#### Load

The primary api is the `immuconf.config/load` function. It takes any
number of arguments. Each argument must be resolvable to a config
file, and can be of any type that is a valid argument for
`clojure.java.io/reader`. `load` returns a standard Clojure map, but
will throw an error if the config file is invalid (that is, if it
contains any values that were specified to have been overridden, but
were not).

```clojure
(def my-cfg (immuconf.config/load "resources/config.edn" "resources/dev.edn"
                                  "~/.private/config.edn" "luke-dev.edn"))
```

If `load` is passed *no* arguments, it will first attempt to read an
`.immuconf.edn` file (relative to the project path), which should be
an EDN file containing a sequence of config file paths.

If there is no `.immuconf.edn` file, it will fall back to reading the
value of the `IMMUCONF_CFG` system environment variable, which should
be a colon-delimited list of config file paths.

If there is neither an `.immuconf.edn` file nor an `IMMUCONF_CFG`
environment variable, the system will throw an error.

#### Get

The only other public function in the API is
`immuconf.config/get`. Its first argument is a config map, any
additional arguments are keys (as would be passed to
`clojure.core/get-in`).

```clojure
(immuconf.config/get my-cfg :database :uri)
```

The only differences between `immuconf.config/get` and
`clojure.core/get` are that the Immuconf version takes varargs instead
of a sequence of keys and that, if the key is missing, it will
throw an info-bearing exception explaining what key was expected but
not found instead of just returning `nil` (which virtually ensures a
`NullPointerExcption` somewhere downstream).

## Logging

Logging of warnings and errors is an important part of Immuconf.

Immuconf logs using `clojure.tools.logging`, which in turn will detect
and use most JVM logging systems. If this doesn't meet your needs,
please file a Github issue and I will see how hard it is to add
support for your logging system.

## License

Copyright © Luke VanderHart 2015

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
