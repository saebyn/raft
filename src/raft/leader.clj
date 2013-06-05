(ns raft.leader
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol ILeader
  (become-candidate [raft] "Makes the raft a candidate.")
  (become-leader [raft] "Makes the raft a leader."))


(defn become-candidate-impl [raft]
  raft)


(defn become-leader-impl [raft]
  raft)


(defn push-impl [raft]
  raft)


(extend Raft
  ILeader
  {:become-candidate become-candidate-impl
   :become-leader become-leader-impl
   :push push-impl})
