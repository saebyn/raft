(ns raft.heartbeat-test
  (:use midje.sweet)
  (:require [raft.heartbeat :refer :all]
            [raft.core :refer :all]))


(facts "about heartbeat"
       (fact "resets election timeout if it hasn't been set"
             (let [raft (create-raft ..rpc.. ..store.. ..state-machine.. ..servers.. :election-timeout 10)]
               (heartbeat raft)) => (contains {:election-timeout-remaining #(>= % 10)})
             (let [raft (-> (create-raft ..rpc.. ..store.. ..state-machine.. ..servers.. :election-timeout 10)
                          (assoc :election-timeout-remaining 1))]
               (heartbeat raft)) => (contains {:election-timeout-remaining 1})))
