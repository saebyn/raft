(ns raft.leader
  (:require [clojure.tools.logging :as l]
            [raft.core :refer [send-rpc-to-all send-rpc persist
                               last-index last-term apply-commits]])
  (:import [raft.core Raft Entry]))

(defprotocol ILeader
  (become-candidate [raft] "Makes the raft a candidate.")
  (become-leader [raft] "Makes the raft a leader.")
  (push [raft] "Pushed pending entries from the log to all followers."))

(defn- vote-response-handler
  [current-term votes [server {term :term vote-granted :vote-granted}]]

  (l/debug
   "Got vote response from" server "term:" term "vote granted:" vote-granted)
  (when term
    ; Update current term to newest
    (swap! current-term max term))
  (when vote-granted
    ; Add the vote, if granted
    (swap! votes inc)))

(defn- request-votes [raft initial-term]
  (let [; Votes for our election, include a vote for ourself.
        votes (atom 1)
        ; The highest seen term, initially the known current term.
        current-term (atom initial-term)]

    ; Dispatch out the RPC to all nodes
    ; Block only until the election timeout expires.
    ; Notice that while we use the election timeout, we don't subtract from
    ; it. This lets us reuse it in the next heartbeat, if we don't become
    ; the leader.

    ; TODO Ideally, we would interrupt the block if/when we get a majority
    ; of the votes, or a majority becomes impossible.
    ; - could use a promise, where the handler checks for majority and resolves
    ;   the promise when its found - use timeout with deref to get timeout effect
    ; UPDATE: yep, this is more possible now with the switch to core.async, so
    ; I should get on that. Also, significantly refactor this code.
    (dorun
     (map
      (partial vote-response-handler current-term votes)
      (send-rpc-to-all raft
                       :request-vote
                       []
                       (:election-timeout-remaining raft))))
    [@current-term @votes]))

(defn become-candidate-impl [raft]
  (l/debug "Entering become candidate")
  (let [raft (-> raft
                 (update-in [:current-term] inc)
                 (assoc :leader-state :candidate))
        initial-term (or (:current-term raft) -1)
        server-count (inc (count (:servers raft)))
        [current-term votes] (request-votes raft initial-term)]
    (l/debug "Highest term seen as candidate:" current-term)
    (l/debug "Votes for this candidate:" votes)
    (if (> current-term initial-term)
      ; Return to being a follower because another server has a more recent
      ; term.
      ; TODO this should probably reset the election timeout too, but
      ; using reset-election-timeout would introduce a circular dependency.
      (-> raft
          (assoc :current-term current-term)
          (assoc :leader-state :follower))

      ; Check to see if we won the election.
      (if (> (/ votes server-count) (/ 1 2))
        ; We won, become a leader
        (become-leader raft)
        ; We lost, remain a candidate for the next round.
        raft))))

(defn become-leader-impl [raft]
  (l/debug "Entering become leader")
  (let [next-index (inc (or (last-index raft) -1))
        rpc-params  [[] (:commit-index raft)]

        raft (-> raft
                 (assoc :leader-state :leader)
                 (update-in [:servers]
                            #(into {}
                                   (map (fn [[server details]]
                                          [server
                                           (assoc details
                                                  :next-index
                                                  next-index)]) %))))]

    (send-rpc-to-all raft :append-entries rpc-params)
    raft))

(defn- get-entries-to-send [raft [server {next-index :next-index}]]
  (assert (<= next-index (count (:log raft))))
  (let [next-entries (map (juxt :term :command)
                          (-> raft
                              :log
                              (subvec next-index)))]
    [server [next-entries (:commit-index raft)]]))

(defn- handle-push-response [sent-entries
                             {raft :raft retries :retries stop :stop}
                             [server {term :term success :success}]]
  (cond
    (true? stop) {:raft raft :retries []}

    ; If follower sends back newer term, use it and become follower
    (> term (:current-term raft)) {:raft (-> raft
                                             (assoc :current-term term)
                                             (assoc :leader-state :follower))
                                   :retries []
                                   :stop true}

    ; If fails, dec next-index and retry
    ;  we retry by building a new map of servers to entries, where
    ;  only the servers we want to retry are included.
    (false? success) {:raft (update-in raft [:servers server :next-index] dec)
                      :retries (conj retries server)}

    ; if succeeds, update next-index
    :else
    (let [sent-entry-count (count (first (sent-entries server)))]
      {:raft (update-in raft [:servers server :next-index]
                        (fnil + -1) sent-entry-count)
       :retries retries})))

(defn- server-log-indices
  "Get the current log entry index of all servers."
  [raft]
  (let [log-length (count (:log raft))
        next-index-fn (comp dec (partial min log-length) :next-index)]
    (map next-index-fn (vals (:servers raft)))))

(defn- is-majority? [raft n]
  ; Add one to servers count to account for this server.
  (let [server-count (-> raft
                         :servers
                         count
                         int)]
    (> n (/ server-count 2))))

(defn- get-majority-index [raft]
  ; Maintain a sorted frequency map of entry indices, which
  ; we alter to find the majority index.
  (loop [entry-index-freqs (into (sorted-map-by >)
                                 (frequencies (server-log-indices raft)))]
    ; Can't do anything if there are no entry indices.
    (when (seq entry-index-freqs)
      (let [[index n] (first entry-index-freqs)
            [next-index _] (second entry-index-freqs)]
        ; `n` is the number of occurances of the most-frequent index `index`
        ; `next-index` is the second-most-frequent index
        (if-not (is-majority? raft n)
          ; If no majority for this index and there are other indices...
          (when next-index
            ; Merge this index's count into the following index, and remove
            ; `index`.
            (recur (-> entry-index-freqs
                       (update-in [next-index] + n)
                     ; Remove this index.
                       (dissoc index))))
          index)))))

(defn- is-majority-holding-current-term? [raft majority-index]
  (assert (< majority-index (count (:log raft))))
  (some #{(:current-term raft)}
        (map :term (subvec (:log raft) 0 (inc majority-index)))))

(defn- update-commit-index [raft]
  (let [majority-index (get-majority-index raft)]
    (if (and majority-index
             (is-majority-holding-current-term? raft majority-index))
      (apply-commits raft majority-index)
      raft)))

(defn push-impl [raft]
  (loop [pending-entries (->> raft
                              :servers
                              (map (partial get-entries-to-send raft))
                              (into {}))]
    (let [responses (send-rpc raft :append-entries pending-entries)
          {raft :raft retries :retries} (reduce
                                         (partial
                                          handle-push-response
                                          pending-entries)
                                         {:raft raft :retries []}
                                         responses)]
      (l/debug
       "Leader push processed responses with" (count retries) "retries")
      (if (seq retries)
        (recur (->> retries
                    (select-keys (:servers raft))
                    (map (partial get-entries-to-send raft))
                    (into {})))
        raft))))

(extend Raft
  ILeader
  {:become-candidate (comp persist become-candidate-impl)
   :become-leader become-leader-impl
   :push (comp persist update-commit-index push-impl)})
