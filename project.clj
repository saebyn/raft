(defproject raft "0.1.0-SNAPSHOT"
  :description "A Clojure library that implements the Raft consensus algorithm."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [slacker "0.9.0"]
                 [cupboard "1.0beta1"]]
  :main raft.demo.core
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})
