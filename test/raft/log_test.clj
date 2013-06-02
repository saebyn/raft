(ns raft.log-test
  (:use midje.sweet)
  (:require [raft.log :refer :all]
            [raft.core :as core]))


(facts "about log"
       (fact "append entry adds the first entry to the log"
         (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)]
           (append-entry raft ..term.. ..command.. nil nil nil) => (contains {:log (just [{:term ..term.. :command ..command..}])}))))
