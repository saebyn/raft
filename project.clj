(defproject raft "0.1.0-SNAPSHOT"
  :description "A Clojure library that implements the Raft consensus algorithm."
  :url "http://github.com/saebyn/raft"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]]
  :main raft.demo.core
  :profiles {:dev {:plugins [[lein-midje "3.1.3"]]
                   :dependencies [[midje "1.6.3"]
                                  [lein-midje "3.1.3"]
                                  [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                                  [org.clojure/tools.cli "0.3.1"]
                                  [clj-logging-config "1.9.12"]
                                  [com.taoensso/nippy "2.6.3"]
                                  [org.zeromq/jeromq "0.3.4"]
                                  [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]]}})
