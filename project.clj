(defproject com.github.jimpil/dblocks "0.1.2-SNAPSHOT"
  :description "Clojure macros for leveraging PostgreSQL advisory locks"
  :url "https://github.com/jimpil/dblocks"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.seancorfield/next.jdbc "1.3.894"]]
  :profiles {:dev {:dependencies [[org.postgresql/postgresql "9.4.1208.jre7"]]}}
  :repl-options {:init-ns com.github.jimpil.dblocks.core}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" ]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ;["vcs" "push"]
                  ]
  :deploy-repositories [["releases" :clojars]] ;; lein release :patch
  :signing {:gpg-key "jimpil1985@gmail.com"}

  )
