(ns raft.vote
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol IVote
  (request-vote [raft candidate-term candidate-server last-log-index last-log-term] "Requests that this raft votes for the candidate"))


(defn request-vote [raft candidate-term candidate-server last-log-index last-log-term]
  {:raft raft :term (:current-term raft) :vote-granted false})


(extend Raft
  IVote
  {:request-vote request-vote})
