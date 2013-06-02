(ns raft.log-test
  (:use midje.sweet)
  (:require [raft.log :refer :all]
            [raft.core :as core]))


(facts "about log"
       (fact "append entry adds the first entry to the log"
             (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)]
               (append-entry raft ..term.. ..command.. nil nil nil) => (contains {:log (just [{:term ..term.. :command ..command..}])})))
       (fact "append entry adds a second entry if the previous entry exists"
             (let [raft (->
                          (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                          (append-entry ..term1.. ..command1.. nil nil nil))]
               (append-entry raft ..term2.. ..command2.. nil ..term1.. 0) => (contains {:log (just [{:term ..term1.. :command ..command1..} {:term ..term2.. :command ..command2..}])})))
       (fact "append entry does nothing if the last index and term isn't present in the log"
             (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)]
               (append-entry raft ..term.. ..command.. nil 1 1) => (contains {:log (just [])}))))

