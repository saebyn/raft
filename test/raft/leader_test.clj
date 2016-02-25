(ns raft.leader-test
  (:use midje.sweet)
  (:require [raft.core :as core]
            [raft.log :as log]
            [raft.heartbeat :as heartbeat]
            [raft.leader :refer :all]
            [clojure.core.async :refer [go]]))

(defn gen-vote-rpc [return-term grants-vote]
  (fn [& rest]
    (go {:term return-term :vote-granted grants-vote :test true})))

(defn gen-append-rpc [return-term return-success]
  (fn [& rest]
    (go {:term return-term :success return-success :test true})))

(defn good-append-rpc [& rest]
  (apply (gen-append-rpc 0 true) rest))

(declare second-state-machine)

(defn state-machine [command]
  [nil second-state-machine])

(defn second-state-machine [command]
  [nil state-machine])

(facts "about election"
       (facts "about become-candidate"
              (facts "when not elected"
                     (let [rpc (gen-vote-rpc 1 false)
                           raft (core/create-raft
                                 rpc --store-- --state-machine--
                                 ..server2.. [..server1.. ..server3..])
                           raft (heartbeat/reset-election-timeout raft)]
                       (fact "increments the current term"
                             (become-candidate raft) =>
                             (contains {:current-term
                                        (inc (:current-term raft))}))

                       (fact "makes the raft a candidate"
                             (become-candidate raft) =>
                             (contains {:leader-state :candidate}))))

              (facts "about getting higher term from request-vote RPC"
                     (fact "updates current term"
                           (let [rpc (gen-vote-rpc 4 false)
                                 raft (core/create-raft
                                       rpc --store-- --state-machine--
                                       ..server2.. [..server1.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                             (become-candidate raft)) =>
                           (contains {:current-term 4}))

                     (fact "becomes a follower"
                           (let [rpc (gen-vote-rpc 4 false)
                                 raft (core/create-raft
                                       rpc --store-- --state-machine--
                                       ..server2.. [..server1.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                             (become-candidate raft)) =>
                           (contains {:leader-state :follower}))

                     (fact "becomes a leader if request-vote responses electing
                            the raft are a majority"
                           (let [rpc (gen-vote-rpc 1 true)
                                 raft (core/create-raft
                                       rpc --store-- --state-machine--
                                       ..server2.. [..server1.. ..server3..])
                                 raft (heartbeat/reset-election-timeout raft)]
                             (become-candidate raft)) =>
                           (contains {:leader-state :leader})))) (facts "about become-leader"
                                                                        (let [raft (-> (core/create-raft
                                                                                        good-append-rpc --store-- --state-machine--
                                                                                        ..server2.. [..server1.. ..server3..])
                                                                                       (assoc :leader-state :candidate))]
                                                                          (fact "makes the raft a leader"
                                                                                (become-leader raft) =>
                                                                                (contains {:leader-state :leader}))

                                                                          (fact "initializes nextIndex for each server to last log index
                       + 1"
                                                                                (become-leader raft) =>
                                                                                (contains {:servers
                                                                                           (just {..server1.. {:next-index 0}
                                                                                                  ..server3.. {:next-index 0}})}))

                                                                          (fact "sends empty append-entries RPC to all servers"
                                                                                (become-leader raft) => anything
                                                                                (provided
                                                                                 (core/send-rpc-to-all anything :append-entries [[] nil]) =>
                                                                                 [] :times 1)))) (let [raft (-> (core/create-raft
                                                                                                                 good-append-rpc --store-- state-machine
                                                                                                                 ..server2.. [..server1.. ..server3..])
                                                                                                                (log/append-entries
                                                                                                                 1 [[1 ..command1..] [2 ..command2..]] nil nil nil)
                                                                                                                :raft
                                                                                                                become-leader)]
                                                                                                   (facts "about push"
                                                                                                          (let [raft (assoc-in raft
                                                                                                                               [:servers ..server1.. :next-index] 0)]
                                                                                                            (fact "sends next batch of entries to each server"
                                                                                                                  (push raft) => anything
                                                                                                                  (provided
                                                                                                                   (core/send-rpc anything :append-entries
                                                                                                                                  {..server1..
                                                                                                                                   [[[1 ..command1..]
                                                                                                                                     [2 .command2..]] nil]
                                                                                                                                   ..server3..
                                                                                                                                   [[] nil]}) =>
                                                                                                                   [] :times 1)))

                                                                                                          (fact "sends empty append-entries RPC when no entries are 
                       pending"
                                                                                                                (push raft) => anything
                                                                                                                (provided
                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server1.. [[] nil]
                                                                                                                                 ..server3.. [[] nil]}) =>
                                                                                                                 [] :times 1))

                                                                                                          (fact "decrements next-index and retries if append-entries RPC 
                       fails due to inconsistency"
                                                                                                                (push raft) => anything
                                                                                                                (provided
                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server1.. [[] nil]
                                                                                                                                 ..server3.. [[] nil]}) =>
                                                                                                                 [[..server1.. {:term 1 :success true}]
                                                                                                                  [..server3.. {:term 1 :success false}]]

                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server3.. [[[2 ..command2..]]
                                                                                                                                              nil]}) =>
                                                                                                                 [[..server3.. {:term 0 :success true}]]))

                                                                                                          (fact "marks entries as committed"
                                                                                                                (push raft) => (contains {:commit-index 1})
                                                                                                                (provided
                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server1.. [[] nil]
                                                                                                                                 ..server3.. [[] nil]}) =>
                                                                                                                 [[..server1.. {:term 1 :success true}]
                                                                                                                  [..server3.. {:term 1 :success true}]]))

                                                                                                          (fact "applies newly commited entries to state machine"
                                                                                                                (push raft) => anything
                                                                                                                (provided
                                                                                                                 (second-state-machine ..command2..) =>
                                                                                                                 [nil state-machine] :times 1

                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server1.. [[] nil]
                                                                                                                                 ..server3.. [[] nil]}) =>
                                                                                                                 [[..server1.. {:term 1 :success true}]
                                                                                                                  [..server3.. {:term 1 :success true}]]))

                                                                                                          (fact "becomes follower if append-entries RPC returns newer 
                       term"
                                                                                                                (push raft) => (contains {:leader-state :follower})
                                                                                                                (provided
                                                                                                                 (core/send-rpc anything :append-entries
                                                                                                                                {..server1.. [[] nil]
                                                                                                                                 ..server3.. [[] nil]}) =>
                                                                                                                 [[..server1.. {:term 3 :success false}]])))))
