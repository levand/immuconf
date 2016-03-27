#?(:cljs (ns immuconf.data-readers
           (:require [immuconf.config]
                     [cljs.reader :as reader])))
#?(:clj {immuconf/override immuconf.config/->Override
         immuconf/default immuconf.config/->Default})
