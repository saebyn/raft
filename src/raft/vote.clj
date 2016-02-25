(ns raft.vote
  (:require [raft.core :refer [persist]]
            [raft.heartbeat :as heartbeat]
            [raft.log :as log])
  (:import [raft.core Raft]))

(defprotocol IVote
  (request-vote
    [raft candidate-term candidate-server last-log-index last-log-term]
    "Requests that this raft votes for the candidate"))

(defn- update-term-if-newer
  [raft new-term]
  (if (> new-term (:current-term raft))
    (-> raft
        (assoc :current-term new-term)
        (assoc :leader-state :follower))
    raft))

(defn request-vote-impl
  [raft candidate-term candidate-server last-log-index last-log-term]
  (let [raft (update-term-if-newer raft candidate-term)
        voted-for (:voted-for raft)
        vote-granted (and
                      (not (< candidate-term (:current-term raft)))
                      (or (nil? voted-for) (= candidate-server voted-for))
                      (log/as-complete? raft last-log-term last-log-index))
        raft (if vote-granted
               (heartbeat/reset-election-timeout raft)
               raft)
        voted-for (if vote-granted
                    candidate-server
                    voted-for)]
    {:raft (assoc raft :voted-for voted-for)
     :term (:current-term raft)
     :vote-granted vote-granted}))

(extend Raft
  IVote
  {:request-vote (fn [raft & rest]
                   (update-in
                    (apply request-vote-impl raft rest) [:raft] persist))})
