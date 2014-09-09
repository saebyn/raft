(ns raft.demo.server
  (:use clojure.tools.logging)
  (:require [raft.heartbeat :refer [heartbeat decrease-election-timeout]]
            [raft.log :refer [append-entries]]
            [raft.vote :refer [request-vote]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]))


(def raft-instance (atom nil))


; Dispatch incoming RPCs via this.
(defmulti rpc (fn [x & rest] x))


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
                       inside)))
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


(defmethod rpc :default [& args]
  (error "Unknown incoming RPC" args))


(defn- rpc-server [zmq-context this-server]
  (info "RPC server started")
  (future
    (with-open [responder (doto (zmq/socket zmq-context :rep)
                            (zmq/bind this-server))]
      (while true
        (let [bytes (zmq/receive responder)
              [command args] (nippy/thaw bytes)]
          (debug "Got RPC" command "with args" args "as" this-server)
          (zmq/send responder (nippy/freeze (apply rpc command args))))))
    (info "RPC server stopped")))


(defn- external-service-server [this-external-server]
  (info "External service server started")
  (future
    ; just sleep for a while so that we don't stop early.
    ; TODO implement me by taking incoming messages,
    ; appending to raft log, if we're the leader.
    ; otherwise, tell the client who the leader is
    (Thread/sleep 10000000000000)
    (info "External service server stopped")))


(defn- heartbeat-server [broadcast-time]
  (info "Heartbeat server started")
  (let [timeout (:election-timeout @raft-instance)]
    (future
      (loop [start (System/currentTimeMillis)]
        (let [current (System/currentTimeMillis)
              elapsed (- current start)
              remaining (- broadcast-time elapsed)]
          (debug "Heartbeat"
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
            (debug "Back from heartbeat impl"))

          (when (pos? remaining)
            (debug "sleeping for" (/ remaining 2) "ms")
            (Thread/sleep (/ remaining 2))
            (debug "Heartbeat back from sleep"))
          (recur current)))
      (info "Heartbeat server stopped"))))


(defn- stats-server []
  (info "Stats server started")
  (future
    (while true
      ; Just dump the contents of the raft state every 5 seconds for now
      ; TODO send stats somewhere?
      (debug "Raft instance:" @raft-instance)
      (Thread/sleep 5000))
    (info "Stats server stopped")))


(defn run-server [zmq-context this-server this-external-server broadcast-time]
  ; TODO stats server optional?
  (let [stopped (promise)
        ; The servers to operate
        operations {:rpc (rpc-server zmq-context this-server)
                    :external (external-service-server this-external-server)
                    :heartbeat (heartbeat-server broadcast-time)
                    :stats (stats-server)}]
    (doseq [[name op] operations]
      (future
        (deliver stopped [name (deref op)])))
    ; Wait until the first component stops, then quit.
    (debug "Exiting component returned" @stopped)
    (info "Server stopping")
    (System/exit 0)))
