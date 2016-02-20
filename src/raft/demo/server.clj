(ns raft.demo.server
  (:require [raft.heartbeat :refer [heartbeat decrease-election-timeout]]
            [raft.log :refer [append-entries]]
            [raft.vote :refer [request-vote]]
            [clojure.tools.logging :as l]
            [clojure.core.async :refer [go-loop alts!! go]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]))

(def raft-instance (atom nil))

; Dispatch incoming RPCs via this.
(defmulti rpc (fn [x & rest]
                (l/debug "rpc dispatch on: " x " with arg count: " (count rest))
                x))

(defn- swap-extract!
  "Like swap!, but takes another function `e` that extracts an inner value
   from the result of applying `f` to the contents of `a` and `args` and
   causes swap! to use that inner value to set the atom `a`.
   
   Returns the `extra` returned by `e`."
  [^clojure.lang.Atom a e f & args]
  (let [extra-atom (atom nil)]
    (apply swap! a (fn [& inner-args]
                     (let [[inside extra] (e (apply f inner-args))]
                       (reset! extra-atom extra)
                       inside)) args)
    @extra-atom))

(defmethod rpc :append-entries
  [command term server last-index last-term entries highest-committed-index]
  (let [extract (fn [{raft :raft term :term success :success}]
                  [raft {:term term :success success}])]
    (swap-extract! raft-instance extract append-entries
                   term entries highest-committed-index last-term last-index)))

(defmethod rpc :request-vote
  [command candidate-term candidate-server last-log-index last-log-term]
  (let [extract (fn [{raft :raft term :term vote-granted :vote-granted}]
                  [raft {:term term :vote-granted vote-granted}])]
    (swap-extract! raft-instance extract request-vote
                   candidate-term candidate-server
                   last-log-index last-log-term)))

(defmethod rpc :is-leader
  [command server]
  "Handle a command to see if this node is the leader - send a boolean response."
  (l/debug  ":is-leader: " command " " server " " raft-instance)
  (l/debug "leader-state: " (:leader-state @raft-instance))
  (= (:leader-state @raft-instance) :leader))

(defmethod rpc :default [& args]
  (l/error "Unknown incoming RPC" args))

(defn- rpc-server [zmq-context this-server]
  (l/info "RPC server started")
  (go 
    (with-open [responder (doto (zmq/socket zmq-context :rep)
                            (zmq/bind this-server))]
      (loop []
        (let [bytes (zmq/receive responder)
              [command args] (nippy/thaw bytes)]
          (l/debug "Got RPC" command "with args" args "as" this-server)
          (zmq/send responder (nippy/freeze (apply rpc command args))))
        (recur)))))

(defn- external-service-server [this-external-server]
  (l/info "External service server started")
  (go-loop []
    ; just sleep for a while so that we don't stop early.
    ; TODO implement me by taking incoming messages,
    ; appending to raft log, if we're the leader.
    ; otherwise, tell the client who the leader is
    (recur)))

(defn- heartbeat-server [broadcast-time]
  (l/info "Heartbeat server started")
  (let [timeout (:election-timeout @raft-instance)]
    (go-loop [start (System/currentTimeMillis)]
      (let [current (System/currentTimeMillis)
            elapsed (- current start)
            remaining (- broadcast-time elapsed)]
        (l/debug "Heartbeat"
                 {:start start
                  :current current
                  :diff elapsed
                  :broadcast-time broadcast-time
                  :remaining remaining})
        (when (> current start)
          ; Here (- current start) is the number of milliseconds we took in
          ; the last iteration, which should be at least broadcast-time ms.
          (swap! raft-instance
                 #(-> %
                      heartbeat
                      (decrease-election-timeout
                       (- current start))))
          (l/debug "Back from heartbeat impl"))

        (when (pos? remaining)
          (l/debug "sleeping for" (/ remaining 2) "ms")
          (Thread/sleep (/ remaining 2))
          (l/debug "Heartbeat back from sleep"))
        (recur current)))))

(defn- stats-server []
  (l/info "Stats server started")
  (go-loop []

      ; Just dump the contents of the raft state every 5 seconds for now
      ; TODO send stats somewhere?
    (l/debug "Raft instance:" @raft-instance)
    (recur)))

(defn run-server [zmq-context this-server this-external-server broadcast-time]
  ; TODO stats server optional?
  (l/debug
   "Exiting component returned"
   (first 
    (alts!!
     [(rpc-server zmq-context this-server)
         ;(external-service-server this-external-server)
      (heartbeat-server broadcast-time)
         ;(stats-server)
])))

  (l/info "Server stopped")
  (System/exit 0))
