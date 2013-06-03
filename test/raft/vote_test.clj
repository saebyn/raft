(ns raft.vote-test
  (:use midje.sweet)
  (:require [raft.vote :refer :all]
            [raft.core :as core]))


(facts "about vote"
       (facts "about request-vote"
              (fact "does nothing if the current term is old"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                                 (assoc :current-term 1))]
                      (request-vote raft 0 ..server1.. ..last-index.. ..last-term..) => {:raft raft :term (:current-term raft) :vote-granted false}))
              (fact "updates the current term if there is a newer term"
                    (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. [..server1.. ..server2..])
                          new-term (inc (:current-term raft))]
                      (request-vote raft new-term ..server1.. ..last-index.. ..last-term..) => (contains {:raft (contains {:current-term new-term}) :term new-term :vote-granted false})))))
