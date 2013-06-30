(ns raft.demo.core
  (gen-class)
  (:use raft.core raft.heartbeat)
  (:require [raft.demo.server :as server]
            [raft.demo.api :as local-api]
            [slacker.client :as slacker]))


(defn- fake-storage
  ([k] nil)
  ([k v] nil))


(defn- fake-state-machine
  [command]
  [nil fake-state-machine])


; TODO we should bail out if a connection cannot be established,
; but right now slacker retries (forever) trying to connect to the
; other end. that must be fixed.
(defn- rpc [server command & args]
  (future
    (let [sc (slacker/slackerc server)]
      (slacker/defn-remote sc run-command
        :remote-ns "raft.demo.api"
        :remote-name (name command)
        :async? true)
      (println "f")
      (let [result @(apply run-command args)]
        (slacker/close-slackerc sc)
        result))))


(defn -main
  "Demo server entry point"
  [& args]
  ; port for rpc?
  ; address to bind to?
  ; election timeout?
  ; db path?
  ; other servers? (address/port)
  ; logging?
  ; 
  (let [this-server "localhost:2104"
        servers []
        timeout 150
        store fake-storage
        raft (create-raft
               rpc store fake-state-machine
               this-server servers
               :election-timeout timeout)
        rpc-address "localhost"
        rpc-port 2104
        rpc-namespace (the-ns 'raft.demo.api)]
    (reset! server/raft-instance raft)
    (server/run-server rpc-namespace rpc-address rpc-port)))
