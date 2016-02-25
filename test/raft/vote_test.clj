(ns raft.vote-test
  (:use midje.sweet)
  (:require [raft.vote :refer :all]
            [raft.log :as log]
            [raft.heartbeat :as heartbeat]
            [raft.core :as core]))

(facts "about vote"
       (let [raft (core/create-raft
                   --rpc-- ..store.. ..state-machine..
                   ..server2.. [..server1..] :election-timeout 2)]
         (facts "about request-vote"
                (fact "does nothing if the current term is old"
                      (let [raft (assoc raft :current-term 1)]
                        (request-vote raft 0 ..server1.. nil nil) =>
                        (contains {:raft raft
                                   :term (:current-term raft)
                                   :vote-granted falsey})))
                (fact "updates the current term if there is a newer term"
                      (let [new-term (inc (:current-term raft))]
                        (request-vote raft new-term ..server1.. nil nil) =>
                        (contains {:raft (contains {:current-term new-term})
                                   :term new-term :vote-granted truthy})))
                (fact "steps down as candidate or leader if there is a newer 
                       term"
                      (let [raft (assoc raft :leader-state :candidate)
                            new-term (inc (:current-term raft))]
                        (request-vote raft new-term ..server1.. nil nil) =>
                        (contains {:raft (contains {:leader-state :follower
                                                    :current-term new-term})
                                   :term new-term :vote-granted truthy})

                        (let [raft (assoc raft :leader-state :leader)
                              new-term (inc (:current-term raft))]
                          (request-vote raft new-term ..server1.. nil nil) =>
                          (contains {:raft (contains {:leader-state :follower
                                                      :current-term new-term})
                                     :term new-term :vote-granted truthy}))))
                (fact "rejects vote if has voted"
                      (let [raft (assoc raft :voted-for ..server2..)
                            new-term (inc (:current-term raft))]
                        (request-vote raft new-term ..server1.. nil nil) =>
                        (contains {:vote-granted falsey})))
                (fact "rejects vote if candidate log is not complete"
                      (let [first-term 0
                            second-term 1
                            raft (-> raft
                                     (log/append-entries
                                      second-term [[first-term ..command1..]
                                                   [second-term ..command2..]]
                                      nil nil nil)
                                     :raft)]
                        (request-vote raft second-term
                                      ..server1.. 0 first-term) =>
                        (contains {:vote-granted falsey})))
                (fact "grants vote if haven't voted and candidate log is
                       complete"
                      (let [first-term 0
                            second-term 1
                            raft (-> raft
                                     (log/append-entries
                                      second-term [[first-term ..command1..]
                                                   [second-term ..command2..]]
                                      nil nil nil)
                                     :raft)]
                        (request-vote
                         raft second-term ..server1.. 1 second-term) =>
                        (contains {:raft (contains {:voted-for ..server1..})
                                   :vote-granted truthy})))
                (fact "grants vote if we've already voted for the candidate and
                       the log is complete"
                      (let [first-term 0
                            second-term 1
                            raft (-> raft
                                     (assoc :voted-for ..server1..)
                                     (log/append-entries
                                      second-term [[first-term ..command1..]
                                                   [second-term ..command2..]]
                                      nil nil nil)
                                     :raft)]
                        (request-vote
                         raft second-term ..server1.. 1 second-term) =>
                        (contains {:raft (contains {:voted-for ..server1..})
                                   :vote-granted truthy})))
                (fact "granting vote resets election timeout"
                      (let [raft (-> raft
                                     heartbeat/reset-election-timeout
                                     (heartbeat/decrease-election-timeout 2))
                            new-term (inc (:current-term raft))]
                        (request-vote raft new-term ..server1.. nil nil) =>
                        (contains {:raft
                                   (contains
                                    {:election-timeout-remaining #(>= % 2)})
                                   :term new-term :vote-granted truthy}))))))
