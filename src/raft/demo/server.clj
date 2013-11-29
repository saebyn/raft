(ns raft.demo.server
  (:use clojure.tools.logging)
  (:require [raft.heartbeat :refer [heartbeat decrease-election-timeout]]
            [raft.log :refer [append-entries]]
            [raft.vote :refer [request-vote]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]))


(def raft-instance (atom nil))


(defmulti rpc (fn [x & rest] x))

; TODO create something like swap!, but that deals with
; functions that return a value that needs to be split by
; another function (e.g. extracting the raft), swapping in
; that into the atom, and then returning the other side of the
; split.

(defmethod rpc :append-entries
  [command term server last-index last-term entries highest-committed-index]
  (let [{raft :raft term :term success :success} (append-entries @raft-instance term entries highest-committed-index last-term last-index)]
    (reset! raft-instance raft)
    {:term term :success success}))

(defmethod rpc :request-vote
  [command candidate-term candidate-server last-log-index last-log-term]
  (let [{raft :raft term :term vote-granted :vote-granted} (request-vote @raft-instance candidate-term candidate-server last-log-index last-log-term)]
    (reset! raft-instance raft)
    {:term term :vote-granted vote-granted}))


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
    ; TODO just sleep for a while so that we don't stop early.
    (Thread/sleep 10000000000000)
    (info "External service server stopped")))


(defn- heartbeat-server [broadcast-time]
  (info "Heartbeat server started")
  (let [timeout (:election-timeout @raft-instance)]
    (future
      (loop [start (System/currentTimeMillis)]
        (let [current (System/currentTimeMillis)]
          (when (> current start)
            ; Here (- current start) is the number of milliseconds we took in
            ; the last iteration, which should be at least broadcast-time ms.
            (swap! raft-instance
                   #(-> %
                    heartbeat
                      (decrease-election-timeout
                        (- current start)))))
          (Thread/sleep (/ (- broadcast-time (- current start)) 1000))
          (recur current)))
      (info "Heartbeat server stopped"))))


(defn- stats-server []
  (info "Stats server started")
  (future
    (while true
      ; Just dump the contents of the raft state every 5 seconds for now
      (debug "Raft instance:" @raft-instance)
      (Thread/sleep 5000))
    (info "Stats server stopped")))


(defn run-server [zmq-context this-server this-external-server broadcast-time]
  ; TODO logging
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
