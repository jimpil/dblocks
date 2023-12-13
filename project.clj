(defproject com.github.jimpil/dblocks "0.1.0-SNAPSHOT"
  :description "Clojure macros for leveraging PostgreSQL advisory locks"
  :url "http://github.com/jimpil/dblocks"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.seancorfield/next.jdbc "1.3.894"]]
  :repl-options {:init-ns com.github.jimpil.dblocks.core})
