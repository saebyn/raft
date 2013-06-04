(ns raft.log-test
  (:use midje.sweet)
  (:require [raft.log :refer :all]
            [raft.heartbeat :as heartbeat]
            [raft.core :as core]))


(facts "about log"
       (facts "about append-entries"
              (fact "adds the first entry to the log"
                    (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success true
                                                                                                 :raft (contains
                                                                                                         {:log (just [{:term ..term.. :command ..command..}])})})))
              (fact "adds a second entry if the previous entry exists"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (append-entries 1 [[..term1.. ..command1..]] nil nil nil)
                                 :raft)]
                      (append-entries raft 1 [[..term2.. ..command2..]] nil ..term1.. 0) => (contains {:success true
                                                                                                       :raft (contains
                                                                                                               {:log (just [{:term ..term1.. :command ..command1..}
                                                                                                                            {:term ..term2.. :command ..command2..}])})})))
              (fact "does nothing if the last index and term isn't present in the log"
                    (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)]
                      (append-entries raft 1 [[..term.. ..command..]] nil 1 1) => (contains {:success false :raft (contains {:log (just [])})})))
              (fact "does nothing and returns current term if provided term is old"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (assoc :current-term 2))]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success false :term 2})))
              (fact "updates current term if new term is newer"
                    (let [raft (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                          new-term 2]
                      (append-entries raft new-term [[..term.. ..command..]] nil nil nil) => (contains {:success true
                                                                                                        :raft (contains {:current-term new-term})
                                                                                                        :term new-term})))
              (fact "if not a follower, becomes one"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (assoc :leader-state :leader))]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success true
                                                                                                 :raft (contains {:leader-state :follower})}))
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (assoc :leader-state :candidate))]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success true
                                                                                                 :raft (contains {:leader-state :follower})})))
              (fact "does not become a follower if the provided term is old"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (assoc :current-term 2)
                                 (assoc :leader-state :leader))]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success false
                                                                                                 :raft (contains {:leader-state :leader})})))
              (fact "resets election timeout"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers.. :election-timeout 2)
                                 heartbeat/reset-election-timeout
                                 (heartbeat/decrease-election-timeout 2))]
                      (append-entries raft 1 [[..term.. ..command..]] nil nil nil) => (contains {:success true
                                                                                                 :raft (contains {:election-timeout-remaining #(>= % 2)})})))

              (fact "if existing entries conflict with new entries, deletes all existing entries starting with first conflicting entry"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (append-entries 2 [[1 ..command0..] [2 ..command1..] [2 ..command2..]] nil nil nil)
                                 :raft)]
                      (append-entries raft 3 [[1 ..commandn1..] [3 ..commandn2..]] nil 1 0) => (contains {:success true
                                                                                      :raft (contains
                                                                                              {:log (just [{:term 1 :command ..command0..}
                                                                                                           {:term 1 :command ..commandn1..}
                                                                                                           {:term 3 :command ..commandn2..}])})})))

              (fact "applies newly committed entries to state machine"
                    (let [raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (append-entries 1 [[..term1.. ..command1..]] nil nil nil)
                                 :raft)]
                      (append-entries raft 1 [[..term2.. ..command2..]] 0 ..term1.. 0)) => (contains {:success true
                                                                                                      :raft (contains
                                                                                                              {:commit-index 0
                                                                                                               :state-machine ..new-state-machine..})})
                    (provided
                      (..state-machine.. ..command1..) => [..result.. ..new-state-machine..])))
                    

       (facts "about as-complete?"
              (fact "If two logs have last entries with different terms,
                    then the log with the later term is more up-to-date."
                    (let [older-term 1
                          newer-term 2
                          raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (append-entries 1 [[older-term ..command..] [older-term ..command..]] nil nil nil)
                                 :raft)]
                      (as-complete? raft newer-term 0) => truthy
                      (as-complete? raft older-term 0) => falsey))
              (fact "If two logs end with the same term, then whichever
                    log is longer is more up-to-date"
                    (let [term 1
                          raft (->
                                 (core/create-raft ..rpc.. ..store.. ..state-machine.. ..servers..)
                                 (append-entries 1 [[term ..command..] [term ..command..]] nil nil nil)
                                 :raft)]
                      (as-complete? raft term 2) => truthy
                      (as-complete? raft term 0) => falsey))))
