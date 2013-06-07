(ns raft.leader-test
  (:use midje.sweet)
  (:require [raft.core :as core]
            [raft.leader :refer :all]))


(facts "about election"
       (facts "about become-candidate"
              (prerequisite
                (--rpc-- ..server1..
                         :request-vote
                         1
                         ..server2..
                         nil
                         nil) => (future {:term 1 :vote-granted false}) :times 1
                (--rpc-- ..server3..
                         :request-vote
                         1
                         ..server2..
                         nil
                         nil) => (future {:term 1 :vote-granted false}) :times 1)

              (let [raft (core/create-raft --rpc-- ..store.. ..state-machine.. ..server2.. [..server1.. ..server2.. ..server3..])]
                (fact "increments the current term"
                      (become-candidate raft) => (contains {:current-term (inc (:current-term raft))}))
  
                (fact "makes the raft a candidate"
                      (become-candidate raft) => (contains {:leader-state :candidate}))

                (facts "about getting higher term from request-vote RPC"
                       (let [raft raft]
                         (future-fact "updates current term")
                         (future-fact "becomes a follower")))

                (future-fact "becomes a leader if request-vote responses electing the raft are a majority")
                (future-fact "persists the raft")))

       (facts "about become-leader"
              (future-fact "makes the raft a leader")
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
