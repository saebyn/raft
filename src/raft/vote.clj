(ns raft.vote
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol IVote
  (request-vote [raft candidate-term candidate-server last-log-index last-log-term] "Requests that this raft votes for the candidate"))


(defn- update-term-if-newer [raft new-term]
  (if (> new-term (:current-term raft))
    (assoc raft :current-term new-term)
    raft))


(defn request-vote [raft candidate-term candidate-server last-log-index last-log-term]
  (let [raft (update-term-if-newer raft candidate-term)]
    {:raft raft :term (:current-term raft) :vote-granted false}))


(extend Raft
  IVote
  {:request-vote request-vote})
