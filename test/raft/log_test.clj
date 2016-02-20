(ns raft.log-test
  (:use midje.sweet)
  (:require [raft.log :refer :all]
            [raft.heartbeat :as heartbeat]
            [raft.core :as core]))

(declare state-machine)

(defn new-state-machine [command]
  [nil state-machine])

(defn state-machine [command]
  [nil new-state-machine])

(facts "about log"
       (let [raft (core/create-raft
                   --rpc-- ..store.. ..state-machine..
                   ..server.. ..servers.. :election-timeout 2)]
         (facts "about append-entries"
                (fact "adds the first entry to the log"
                      (append-entries raft 1
                                      [[..term.. ..command..]] nil nil nil) =>
                      (contains
                       {:success true
                        :raft (contains
                               {:log (just [(contains
                                             {:term ..term..
                                              :command ..command..})])})}))
                (fact "adds a second entry if the previous entry exists"
                      (let [raft (-> raft 
                                     (append-entries 1 [[..term1.. ..command1..]]
                                                     nil nil nil)
                                     :raft)]
                        (append-entries raft 1
                                        [[..term2.. ..command2..]]
                                        nil ..term1.. 0) =>
                        (contains
                         {:success true
                          :raft
                          (contains
                           {:log (just
                                  [(contains {:term ..term1..
                                              :command ..command1..})
                                   (contains {:term ..term2..
                                              :command ..command2..})])})})))
                (fact "does nothing if the last index and term isn't present in
                       the log"
                      (append-entries raft 1
                                      [[..term.. ..command..]] nil 1 1) =>
                      (contains {:success false
                                 :raft (contains {:log (just [])})}))
                (fact "does nothing and returns current term if provided term
                       is old"
                      (let [raft (assoc raft :current-term 2)]
                        (append-entries raft 1
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains {:success false :term 2})))
                (fact "updates current term if new term is newer"
                      (let [new-term 2]
                        (append-entries raft new-term
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains
                         {:success true
                          :raft (contains {:current-term new-term})
                          :term new-term})))
                (fact "if not a follower, becomes one"
                      (let [raft (assoc raft :leader-state :leader)]
                        (append-entries raft 1
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains
                         {:success true
                          :raft (contains {:leader-state :follower})}))
                      (let [raft (assoc raft :leader-state :candidate)]
                        (append-entries raft 1
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains
                         {:success true
                          :raft (contains {:leader-state :follower})})))
                (fact "does not become a follower if the provided term is old"
                      (let [raft (-> raft 
                                     (assoc :current-term 2)
                                     (assoc :leader-state :leader))]
                        (append-entries raft 1
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains {:success false
                                   :raft (contains {:leader-state :leader})})))
                (fact "resets election timeout"
                      (let [raft (-> raft
                                     heartbeat/reset-election-timeout
                                     (heartbeat/decrease-election-timeout 2))]
                        (append-entries raft 1
                                        [[..term.. ..command..]]
                                        nil nil nil) =>
                        (contains
                         {:success true
                          :raft (contains
                                 {:election-timeout-remaining #(>= % 2)})})))

                (fact "if existing entries conflict with new entries, deletes
                       all existing entries starting with first conflicting
                       entry"
                      (let [raft (-> raft
                                     (append-entries 2 [[1 ..command0..]
                                                        [2 ..command1..]
                                                        [2 ..command2..]]
                                                     nil nil nil)
                                     :raft)
                            expected-log [{:term 1 :command ..command0..}
                                          {:term 1 :command ..commandn1..}
                                          {:term 3 :command ..commandn2..}]]
                        (append-entries raft 3
                                        [[1 ..commandn1..]
                                         [3 ..commandn2..]] nil 1 0) =>
                        (contains
                         {:success true
                          :raft (contains
                                 {:log (just
                                        (map contains expected-log))})})))

                (fact "applies newly committed entries to state machine"
                      ; This breaks if we use the raft constructed in the outer
                      ; scope. I don't know why.
                      (let [raft (-> (core/create-raft
                                      --rpc-- --store-- state-machine
                                      ..server.. ..servers..
                                      :election-timeout 2)
                                     (append-entries 1
                                                     [[..term1.. ..command1..]]
                                                     nil nil nil)
                                     :raft)]
                        (append-entries raft 1
                                        [[..term2.. ..command2..]]
                                        0 ..term1.. 0)) =>
                      (contains
                       {:success true
                        :raft (contains {:commit-index 0
                                         :state-machine new-state-machine})})
                      (provided
                       (state-machine ..command1..) =>
                       [..result.. new-state-machine]))) (facts "about as-complete?"
                                                                (fact "If two logs have last entries with different terms,
                      then the log with the later term is more up-to-date."
                                                                      (let [older-term 1
                                                                            newer-term 2
                                                                            raft (-> raft
                                                                                     (append-entries 1 [[older-term ..command..]
                                                                                                        [older-term ..command..]]
                                                                                                     nil nil nil)
                                                                                     :raft)]
                                                                        (as-complete? raft newer-term 0) => truthy
                                                                        (as-complete? raft older-term 0) => falsey))
                                                                (fact "If two logs end with the same term, then whichever
                      log is longer is more up-to-date"
                                                                      (let [term 1
                                                                            raft (-> raft
                                                                                     (append-entries 1 [[term ..command..]
                                                                                                        [term ..command..]]
                                                                                                     nil nil nil)
                                                                                     :raft)]
                                                                        (as-complete? raft term 2) => truthy
                                                                        (as-complete? raft term 0) => falsey)))))
