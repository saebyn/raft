(ns raft.vote
  (:use raft.core)
  (:use raft.log)
  (:import [raft.core Raft]))


(defprotocol IVote
  (request-vote [raft candidate-term candidate-server last-log-index last-log-term] "Requests that this raft votes for the candidate"))


(defn- update-term-if-newer [raft new-term]
  (if (> new-term (:current-term raft))
    (-> raft
      (assoc :current-term new-term)
      (assoc :leader-state :follower))
    raft))


(defn request-vote [raft candidate-term candidate-server last-log-index last-log-term]
  (let [raft (update-term-if-newer raft candidate-term)]
    {:raft raft
     :term (:current-term raft)
     :vote-granted (as-complete? raft last-log-term last-log-index)}))


(extend Raft
  IVote
  {:request-vote request-vote})
