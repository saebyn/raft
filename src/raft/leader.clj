(ns raft.leader
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol ILeader
  (become-candidate [raft] "Makes the raft a candidate.")
  (become-leader [raft] "Makes the raft a leader."))


(defn become-candidate-impl [raft]
  (let [raft (-> raft
               (update-in [:current-term] inc)
               (assoc :leader-state :candidate))
        params ((juxt :current-term :this-server last-index last-term) raft)
        servers (remove #(= (:this-server raft) %) (keys (:servers raft)))]
    (doall
      (map #(apply (:rpc raft) % :request-vote params) servers))
    raft))


(defn become-leader-impl [raft]
  raft)


(defn push-impl [raft]
  raft)


(extend Raft
  ILeader
  {:become-candidate become-candidate-impl
   :become-leader become-leader-impl
   :push push-impl})
