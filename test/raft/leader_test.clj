(ns raft.leader-test
  (:use midje.sweet)
  (:require [raft.core :as core]
            [raft.heartbeat :as heartbeat]
            [raft.leader :refer :all]))


(defn generate-rpc [return-term grants-vote server method term source-server last-index last-term]
  (future {:term return-term :vote-granted grants-vote :test true}))


(facts "about election"
       (facts "about become-candidate"
              (facts "when not elected"
                     (let [rpc (partial generate-rpc 1 false)
                           raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server2.. ..server3..])
                           raft (heartbeat/reset-election-timeout raft)]
                       (fact "increments the current term"
                             (become-candidate raft) => (contains {:current-term (inc (:current-term raft))}))
  
                       (fact "makes the raft a candidate"
                             (become-candidate raft) => (contains {:leader-state :candidate}))))

              (facts "about getting higher term from request-vote RPC"
                     (fact "updates current term"
                           (let [rpc (partial generate-rpc 4 false)
                                 raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server2.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                                    (become-candidate raft)) => (contains {:current-term 4}))
  
                     (fact "becomes a follower"
                           (let [rpc (partial generate-rpc 4 false)
                                 raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server2.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                                    (become-candidate raft)) => (contains {:leader-state :follower}))

              (future-fact "becomes a leader if request-vote responses electing the raft are a majority")

              (future-fact "persists the raft")))


       (facts "about become-leader"
              (future-fact "makes the raft a leader"
                           )
              (future-fact "initializes nextIndex for each server to last log index + 1")
              (future-fact "sends empty append-entries RPC to all servers"))

       (facts "about push"
              (future-fact "sends next batch of entries to each server")
              (future-fact "sends empty append-entries RPC when no entries are pending")
              (future-fact "decrements next-index and retries if append-entries RPC fails due to inconsistency")
              (future-fact "marks entries as committed")
              (future-fact "applies newly commited entries to state machine")
              (future-fact "becomes follower if append-entries RPC returns newer term")
              (future-fact "persists raft when done")))
