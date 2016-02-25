(ns raft.log
  (:require [raft.core :as core]
            [raft.heartbeat :as heartbeat])
  (:import [raft.core Raft Entry]))

(defprotocol ILog
  (append-entries
    [raft term entries highest-committed-index last-term last-index]
    "Appends the entries to the raft's log")
  (as-complete?
    [raft last-term last-index]
    "Returns non-nil if the term and index indicate a log at least as complete
     as the raft's log"))

(defn- entry-exists? [raft term index]
  (or (and (nil? term) (nil? index))
      (let [entry (get (:log raft) index)]
        (and (not (nil? entry))
             (= (:term entry) term)))))

(defn- make-entry [[term command]]
  (Entry. term command nil))

(defn- add-entries [[raft entries]]
  (update-in raft [:log] #(into % entries)))

(defn- find-first-conflict [log entries]
  (.indexOf (mapv not= (mapv :term log) (mapv :term entries)) true))

(defn- remove-conflicting-entries
  [raft last-index entries]
  (assert (or
           (and (nil? last-index) (zero? (count (:log raft))))
           (>= (dec (count (:log raft))) last-index)))
  (if (nil? last-index)
    [raft entries]
    (let [start-index (inc last-index)]
      (if (nil? (get-in raft [:log start-index]))
        [raft entries]
        (let [conflict-index (-> raft
                                 :log
                                 (subvec start-index)
                                 (find-first-conflict entries))
              log (-> raft
                      :log
                      (subvec 0 (+ start-index conflict-index)))
              raft (assoc raft :log log)]
          [raft entries])))))

(defn- commit-entries
  [raft last-index highest-committed-index entries]
  (-> raft
      (remove-conflicting-entries last-index (map make-entry entries))
      add-entries
      (core/apply-commits highest-committed-index)))

(defn append-entries-impl
  [raft term entries highest-committed-index last-term last-index]
  (let [current-term (:current-term raft)]
    (if (< term current-term)
      ; Discard entries if the term is old.
      {:raft raft :term current-term :success false}

      ; Otherwise,
      ; at this point the provided term is at least as up-to-date
      ; as the current term, we can always use the provided term instead.
      (let [raft (-> raft
                     (assoc :current-term term)
                     (assoc :leader-state :follower)
                     heartbeat/reset-election-timeout)
            success (entry-exists? raft last-term last-index)
            raft (if success
                   (commit-entries raft
                                   last-index highest-committed-index
                                   entries)
                   raft)]
        {:raft raft :term term :success success}))))

(defn as-complete?
  [raft term index]
  (if (= (core/last-term raft) term)
    (<= (count (:log raft)) (inc (or index 0)))
    (<= (or (core/last-term raft) -1) (or term -1))))

(extend Raft
  ILog
  {:append-entries (fn [raft & rest]
                     (update-in
                      (apply append-entries-impl raft rest) [:raft] core/persist))

   :as-complete? as-complete?})
