(ns raft.heartbeat
  (:require [clojure.tools.logging :as l]
            [raft.leader :as leader])
  (:import [raft.core Raft]))

(defprotocol IHeartbeat
  (reset-election-timeout
    [raft] "Resets the remaining time until an election starts.")
  (decrease-election-timeout
    [raft amount] "Decreases the remaining time until an election starts.")
  (heartbeat
    [raft] "Beats the heart of the raft"))

(defn decrease-election-timeout [raft amount]
  (l/debug "decreasing election timeout" raft amount)
  (update-in raft [:election-timeout-remaining] #(max 0 (- % amount))))

(defn- generate-timeout
  "Generates a uniform random timeout in the range 
   [base-timeout, 2*base-timeout)."
  [base-timeout]
  (+ base-timeout (rand-int base-timeout)))

(defn- election-timer-unset? [raft]
  (nil? (:election-timeout-remaining raft)))

(defn heartbeat [raft]
  (l/debug "Entering heartbeat")
  (if (election-timer-unset? raft)
    (reset-election-timeout raft)
    (cond
      (= :leader (:leader-state raft))
      (do
        (l/debug "Heartbeat pushing as leader")
        (let [raft (leader/push raft)]
          (l/debug "got raft back from leader push" raft)
          raft))
      (pos? (:election-timeout-remaining raft))
      (do
        (l/debug "Heartbeat doing nothing")
        raft)
      ; Candidates need the election timeout reset. Do it here to avoid
      ; circular deps.
      :else
      (do
        (l/debug "Heartbeat becoming candidate")
        (leader/become-candidate (reset-election-timeout raft))))))

(defn reset-election-timeout [raft]
  (l/debug "Resetting election timeout")
  (assoc raft
         :election-timeout-remaining
         (generate-timeout (:election-timeout raft))))

(extend Raft
  IHeartbeat
  {:heartbeat heartbeat
   :decrease-election-timeout decrease-election-timeout
   :reset-election-timeout reset-election-timeout})
