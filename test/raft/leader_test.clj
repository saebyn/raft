(ns raft.leader-test
  (:use midje.sweet)
  (:require [raft.core :as core]
            [raft.heartbeat :as heartbeat]
            [raft.leader :refer :all]))


(defn gen-vote-rpc [return-term grants-vote]
  (fn [server method term source-server last-index last-term]
    (future {:term return-term :vote-granted grants-vote :test true})))

(defn gen-append-rpc [return-term return-success]
  (fn [server method term leader-id prev-log-index prev-log-term entries commit-index]
    (future {:term return-term :success return-success})))


(facts "about election"
       (facts "about become-candidate"
              (facts "when not elected"
                     (let [rpc (gen-vote-rpc 1 false)
                           raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server3..])
                           raft (heartbeat/reset-election-timeout raft)]
                       (fact "increments the current term"
                             (become-candidate raft) => (contains {:current-term (inc (:current-term raft))}))
  
                       (fact "makes the raft a candidate"
                             (become-candidate raft) => (contains {:leader-state :candidate}))))

              (facts "about getting higher term from request-vote RPC"
                     (fact "updates current term"
                           (let [rpc (gen-vote-rpc 4 false)
                                 raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                                    (become-candidate raft)) => (contains {:current-term 4}))
  
                     (fact "becomes a follower"
                           (let [rpc (gen-vote-rpc 4 false)
                                 raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                                    (become-candidate raft)) => (contains {:leader-state :follower}))

              (fact "becomes a leader if request-vote responses electing the raft are a majority"
                    (let [rpc (gen-vote-rpc 1 true)
                          raft (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server3..])
                          raft (heartbeat/reset-election-timeout raft)]
                      (become-candidate raft)) => (contains {:leader-state :leader}))))


       (facts "about become-leader"
              (let [rpc (gen-append-rpc 0 true)
                    raft (-> (core/create-raft rpc ..store.. ..state-machine.. ..server2.. [..server1.. ..server3..])
                           (assoc :leader-state :candidate))]
                (fact "makes the raft a leader"
                      (become-leader raft) => (contains {:leader-state :leader}))

                (fact "initializes nextIndex for each server to last log index + 1"
                      (become-leader raft) => (contains {:servers (just {..server1.. {:next-index 0}
                                                                         ..server3.. {:next-index 0}})}))


                (future-fact "sends empty append-entries RPC to all servers")))

       (facts "about push"
              (future-fact "sends next batch of entries to each server")
              (future-fact "sends empty append-entries RPC when no entries are pending")
              (future-fact "decrements next-index and retries if append-entries RPC fails due to inconsistency")
              (future-fact "marks entries as committed")
              (future-fact "applies newly commited entries to state machine")
              (future-fact "becomes follower if append-entries RPC returns newer term")))
