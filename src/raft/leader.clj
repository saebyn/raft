(ns raft.leader
  (:use raft.core)
  (:import [raft.core Raft]))


(defprotocol ILeader
  (become-candidate [raft] "Makes the raft a candidate.")
  (become-leader [raft] "Makes the raft a leader."))


(defn- vote-response-handler [server rpc params current-term votes]
  ; Block on response to RPC.
  (let [pending-request (apply rpc server :request-vote params)]
    (let [{term :term
           vote-granted :vote-granted} @pending-request]
      ; Update current term to newest
      (swap! current-term max term)
      ; Add the vote, if granted
      (when vote-granted
        (swap! votes inc)))))


(defn- get-server-requests [raft]
  ; Although the servers map shouldn't include this server in it,
  ; best to be safe and make sure it's not in there.
  (map agent (remove #(= (:this-server raft) %) (keys (:servers raft)))))


(defn become-candidate-impl [raft]
  (let [raft (-> raft
               (update-in [:current-term] inc)
               (assoc :leader-state :candidate))
        initial-term (:current-term raft)

        rpc-params ((juxt :current-term :this-server last-index last-term) raft)


        ; Votes for our election, include a vote for ourself.
        votes (atom 1)
        ; The highest seen term, initially the known current term.
        current-term (atom initial-term)

        requests (get-server-requests raft)
        server-count (inc (count requests))]

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
    (apply await-for (:election-timeout-remaining raft)
           (map #(send-off % vote-response-handler (:rpc raft) rpc-params current-term votes) requests))

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
        rpc-params (-> raft
                     ((juxt :current-term :this-server last-index last-term))
                     vec
                     (conj []) ; Add empty entries arg
                     (conj (:commit-index raft)))

        requests (get-server-requests raft)
        
        raft (-> raft
               (assoc :leader-state :leader)
               (update-in [:servers]
                          #(into {}
                                 (map (fn [[server details]]
                                        [server (assoc details :next-index next-index)]) %))))]

    (doseq [req requests]
      (set-error-mode! req :contine)
      (set-error-handler! req (fn [ag ex]
                                ; TODO logging?
                                (println "rpc failure"  ag ex)
                              )))

    (apply await (map
             #(send-off % (fn [server] @(apply (:rpc raft) server :append-entry rpc-params)))
             requests))
    raft))


(defn push-impl [raft]
  raft)


(extend Raft
  ILeader
  {:become-candidate (comp persist become-candidate-impl)
   :become-leader become-leader-impl
   :push (comp persist push-impl)})
