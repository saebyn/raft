(ns raft.demo.server
  (:use clojure.tools.logging)
  (:require [raft.heartbeat :refer [heartbeat decrease-election-timeout]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]))


(def raft-instance (atom nil))


(defn- rpc-server [zmq-context this-server]
  (info "RPC server started")
  (future
    (with-open [responder (doto (zmq/socket zmq-context :rep)
                            (zmq/bind this-server))]
      (while true
        (let [[command args] (nippy/thaw (zmq/receive-all responder))]
          (debug "Got RPC" command "with args" args "as" this-server)
          ; Return response
          (zmq/send responder (nippy/freeze "world")))))
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
      (loop []
        (let [start (System/currentTimeMillis)]
          (swap! raft-instance
                 #(-> %
                  heartbeat
                    (decrease-election-timeout
                      (- start (System/currentTimeMillis)))))
          (Thread/sleep (- broadcast-time (- start (System/currentTimeMillis)))))
        (recur))
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
