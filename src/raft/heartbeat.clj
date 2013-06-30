(ns raft.heartbeat
  (:use raft.core)
  (:require [raft.leader :as leader])
  (:import [raft.core Raft]))


(defprotocol IHeartbeat
  (reset-election-timeout [raft] "Resets the remaining time until an election starts.")
  (decrease-election-timeout [raft amount] "Decreases the remaining time until an election starts.")
  (heartbeat [raft] "Beats the heart of the raft"))


(defn decrease-election-timeout [raft amount]
  (update-in raft [:election-timeout-remaining] #(- % amount)))


(defn- generate-timeout
  "Generates a uniform random timeout in the range [base-timeout, 2*base-timeout)."
  [base-timeout]
  (+ base-timeout (rand-int base-timeout)))


(defn heartbeat [raft]
  (if (nil? (:election-timeout-remaining raft))
    (reset-election-timeout raft)
    (cond
      (= :leader (:leader-state raft)) (leader/push raft)
      (pos? (:election-timeout-remaining raft)) raft
      :else (leader/become-candidate raft))))


(defn reset-election-timeout [raft]
  (assoc raft
         :election-timeout-remaining (generate-timeout (:election-timeout raft))))


(extend Raft
  IHeartbeat
  {:heartbeat heartbeat
   :decrease-election-timeout decrease-election-timeout
   :reset-election-timeout reset-election-timeout})
