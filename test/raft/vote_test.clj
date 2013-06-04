(ns raft.vote-test
  (:use midje.sweet)
  (:require [raft.vote :refer :all]
            [raft.log :as log]
            [raft.core :as core]))


(facts "about vote"
       (facts "about request-vote"
              (fact "does nothing if the current term is old"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (assoc :current-term 1))]
                      (request-vote raft 0 ..server1.. ..last-index.. ..last-term..) => (contains {:raft raft :term (:current-term raft) :vote-granted falsey})))
              (fact "updates the current term if there is a newer term"
                    (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                          new-term (inc (:current-term raft))]
                      (request-vote raft new-term ..server1.. ..last-index.. ..last-term..) => (contains {:raft (contains {:current-term new-term}) :term new-term :vote-granted falsey})))
              (fact "steps down as candidate or leader if there is a newer term"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (assoc :leader-state :candidate))
                          new-term (inc (:current-term raft))]
                      (request-vote raft new-term ..server1.. ..last-index.. ..last-term..) => (contains {:raft (contains {:leader-state :follower :current-term new-term}) :term new-term :vote-granted falsey})
                      (let [raft (assoc raft :leader-state :leader)
                            new-term (inc (:current-term raft))]
                        (request-vote raft new-term ..server1.. ..last-index.. ..last-term..) => (contains {:raft (contains {:leader-state :follower :current-term new-term}) :term new-term :vote-granted falsey}))))
              (fact "rejects vote if has voted"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (assoc :voted-for ..server2..))
                          new-term (inc (:current-term raft))]
                      (request-vote raft new-term ..server1.. ..last-index.. ..last-term..) => (contains {:vote-granted falsey})))
              (fact "rejects vote if candidate log is not complete"
                    (let [first-term 0
                          second-term 1
                          raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (log/append-entries second-term [[first-term ..command1..] [second-term ..command2..]] nil nil nil))]
                      (request-vote raft second-term ..server1.. 0 first-term) => (contains {:vote-granted falsey})))
              (fact "grants vote if haven't voted and candidate log is complete"
                    (let [first-term 0
                          second-term 1
                          raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (log/append-entries second-term [[first-term ..command1..] [second-term ..command2..]] nil nil nil))]
                      (request-vote raft second-term ..server1.. 1 second-term) => (contains {:raft (contains {:voted-for ..server1..}) :vote-granted truthy})))
              (fact "grants vote if we've already voted for the candidate and the log is complete"
                    (let [first-term 0
                          second-term 1
                          raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (assoc :voted-for ..server1..)
                                 (log/append-entries second-term [[first-term ..command1..] [second-term ..command2..]] nil nil nil))]
                      (request-vote raft second-term ..server1.. 1 second-term) => (contains {:raft (contains {:voted-for ..server1..}) :vote-granted truthy})))))
