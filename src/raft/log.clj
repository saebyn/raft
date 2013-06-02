(ns raft.log
  (:use raft.core)
  (:import [raft.core Raft]))


(defrecord Entry [term command])

(defprotocol ILog
  (append-entry [raft term command highest-committed-index last-term last-index] "Appends an entry to the raft's log")
  (as-complete? [raft current-term last-term last-index] "Returns non-nil if the last item is in the log and an item in the log has the current term"))


(defn- entry-exists? [log term index]
  (or (and (nil? term) (nil? index))
      (let [entry (get log index)]
        (and (not (nil? entry))
             (= (:term entry) term)))))


(defn- append-entry-command [log term command]
  (conj log (Entry. term command)))


(defn append-entry [raft term command highest-committed-index last-term last-index]
  (if (entry-exists? (:log raft) last-term last-index)
    (update-in raft [:log] append-entry-command term command)
    raft))


(defn as-complete? [raft current-term last-term last-index]
  (and (entry-exists? (:log raft) last-term last-index)
       (seq (filter #(= current-term %) (map :term (:log raft))))))


(extend Raft
  ILog
  {:append-entry append-entry
   :as-complete? as-complete?})
