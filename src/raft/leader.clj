(ns raft.leader
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol ILeader
  (become-candidate [raft] "Makes the raft a candidate.")
  (become-leader [raft] "Makes the raft a leader.")
  (push [raft] "Pushed pending entries from the log to all followers."))


(defn- vote-response-handler
  [current-term votes [server {term :term vote-granted :vote-granted}]]
  ; Update current term to newest
  (swap! current-term max term)
  ; Add the vote, if granted
  (when vote-granted
    (swap! votes inc)))


(defn become-candidate-impl [raft]
  (let [raft (-> raft
               (update-in [:current-term] inc)
               (assoc :leader-state :candidate))
        initial-term (:current-term raft)

        ; Votes for our election, include a vote for ourself.
        votes (atom 1)
        ; The highest seen term, initially the known current term.
        current-term (atom initial-term)

        server-count (inc (count (:servers raft)))]

    ; Dispatch out the RPC to each request agent
    ; Block for all agents to finish the RPC operation, but only until
    ; the election timeout expires.
    ; Notice that while we use the election timeout, we don't subtract from
    ; it. This lets us reuse it in the next heartbeat, if we don't become
    ; the leader.
    ; TODO Ideally, we would interrupt the block if/when we get a majority
    ; of the votes, or a majority becomes impossible.
    ; - could use a promise, where the handler checks for majority and resolves
    ;   the promise when its found - use timeout with deref to get timeout effect
    (dorun
      (map
        (partial vote-response-handler current-term votes)
        (send-rpc raft
                  :request-vote
                  []
                  (:election-timeout-remaining raft))))

    (if (> @current-term initial-term)
      ; Return to being a follower because another server has a more recent
      ; term.
      (-> raft
        (assoc :current-term @current-term)
        (assoc :leader-state :follower))

      ; Check to see if we won the election.
      (if (> (/ @votes server-count) (/ 1 2))
        ; We won, become a leader
        (become-leader raft)
        ; We lost, remain a candidate for the next round.
        raft))))


(defn become-leader-impl [raft]
  (let [next-index (inc (or (last-index raft) -1))
        rpc-params  [[] (:commit-index raft)]

        raft (-> raft
               (assoc :leader-state :leader)
               (update-in [:servers]
                          #(into {}
                                 (map (fn [[server details]]
                                        [server
                                         (assoc details :next-index next-index)]) %))))]

    (send-rpc raft :append-entries rpc-params)
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
                          (partial + sent-entry-count))
           :retries retries})))


(defn push-impl [raft]
  (loop [pending-entries (->> raft
                          :servers
                          (map (partial get-entries-to-send raft))
                          (into {}))]
    (let [responses (send-rpc raft :append-entries pending-entries)
          {raft :raft retries :retries} (reduce
                                          (partial handle-push-response pending-entries)
                                          {:raft raft :retries []}
                                          responses)]
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
   :push (comp persist push-impl)})
