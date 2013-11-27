(defproject raft "0.1.0-SNAPSHOT"
  :description "A Clojure library that implements the Raft consensus algorithm."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.cli "0.2.4"]
                 [org.clojure/tools.logging "0.2.6"]
                 [clj-logging-config "1.9.10"]
                 [com.taoensso/nippy "2.5.0"]
                 [org.zeromq/jeromq "0.3.1"]
                 [org.zeromq/cljzmq "0.1.3" :exclusions [org.zeromq/jzmq]]
                 [cupboard "1.0beta1"]]
  :main raft.demo.core
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})
