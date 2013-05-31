(ns raft.log
  (:use raft.core)
  (:import [raft.core Raft]))


(defrecord Entry [term command])

(defprotocol ILog
  (append-entry [raft term command highest-committed-index last-term last-index] "Appends an entry to the raft's log"))


(defn entry-exists? [log term index]
  false)


(defn append-entry-command [log term command]
  (conj log (Entry. term command)))


(defn append-entry [raft term command highest-committed-index last-term last-index]
  (let [log (:log raft)]
    (when (or (empty? log) (entry-exists? log last-term last-index))
      (append-entry-command (:log raft) term command))))


(extend Raft
  ILog
  {:append-entry append-entry})
