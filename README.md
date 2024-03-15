# dblocks

## What
A bunch of Clojure macros for leveraging PostgreSQL advisory-locks. 
These come at two levels - `session` VS `transaction`. 
There is also the `shared` VS `exclusive` distinction, but we are only concerned with `exclusive` locks here.

## Why 
Advisory locks are essentially lightweight, general-purpose, **distributed** locks, and as such, can be very useful 
in a variety of distributed scenarios (at the application level).

## Where
[![Clojars Project](https://clojars.org/com.github.jimpil/dblocks/latest-version.svg)](https://clojars.org/com.github.jimpil/flog)


## How

There are 6 (code-wrapping) macros in `core.clj` to choose from (depending on your use-case):

### session (with explicit lock-release in a `try-finally`)

- `with-session-lock [db id & body]`     (waits as long as necessary)
- `with-session-try-lock [db id & body]` (returns immediately)
- `with-session-try-lock-timeout [db id n & body]` (waits up to `n` seconds)

### transaction (with implcit lock-release) 

- `with-transaction-lock [db id & body]`     (waits as long as necessary)
- `with-transaction-try-lock [db id & body]` (returns immediately)
- `with-transaction-try-lock-timeout [db id n & body]` (waits up to `n` seconds)

If unsure, use a session-lock. If the code you're wrapping involves a DB transaction anyway, 
you might as well use a transaction-lock (nested transactions on the same connection are flattened anyway). 

## Locking IDs
PostgreSQL expects signed 64-bit integers (i.e. Java longs) as the key/id when creating/querying locks. 
However, you are also allowed to use String/Keyword/Symbol (which are turned into a Long via `clojure.core/hash`). 
It is possible to use `nil`, but that will create a random long (via `ThreadLocalRandom`). 
In theory, there could be collisions with this, so don't use outside of development.
That said, unless you're creating millions of them, it is fairly unlikely to hit collisions,
so in practice, it's not the end of the world. Anything else will throw.

## Integration with `java.util.concurrent.locks`
There is also `core/pg-session-lock [db]`, which as the name implies, returns a (partial) implementation of 
`java.util.concurrent.locks.Lock` wrapping a PostgreSQL session lock. 
See below on an idea around why/how that might be useful.

## Integration with `duratom`
[duratom](https://github.com/jimpil/duratom) is a durable atom type for Clojure, supporting pluggable storage backends (postgres included). It is a very useful primitive to have in-process, 
but it cannot be used 
in a distributed setting as there would eventually be race conditions. Thankfully, `duratom` exposes the lock it uses (to protect against in-process concurrency) as a parameter (i.e. a `java.util.concurrent.locks.Lock`). This basically means that if you have some kind of distributed Lock instance
at hand (like the one returned by `core/pg-session-lock`), you can turn  
an existing `duratom` (configured with a postgres backend), into a
 fully distributed construct, by simply initialising it with two extra keys:

 - `:lock (pg-session-lock ...)` => tells duratom to use this custom lock
 - `:read-from :db` => tells duratom to read from storage -  NOT from memory (will be slower of course)  


## License

Copyright © 2023 Dimitrios Piliouras

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
