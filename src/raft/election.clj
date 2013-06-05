(ns raft.election
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol IElectable
  (become-candidate [raft] "Makes the raft a candidate."))


(defn become-candidate-impl [raft]
  raft)


(extend Raft
  IElectable
  {:become-candidate become-candidate-impl})
