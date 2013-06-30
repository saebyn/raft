(ns raft.demo.core
  (gen-class)
  (:use raft.core raft.heartbeat)
  (:require [raft.demo.server :as server]
            [raft.demo.api :as local-api]
            [slacker.client :as slacker]))


(defn- fake-state-machine
  [command]
  [nil fake-state-machine])


(defn rpc [server command & args]
  (future
    (let [sc (slacker/slackerc server)]
      (slacker/defn-remote sc run-command
        :remote-ns "raft.demo.api"
        :remote-name (name command)
        :async? true)
      (let [result @(apply run-command args)]
        (slacker/close-slackerc sc)
        result))))


(defn -main
  "Demo server entry point"
  [& args]
  ; port for rpc?
  ; address to bind to?
  ; db path?
  ; logging?
  ; 
  (println args)
  ; TODO set up the server/raft-instance atom
  (server/run-server (the-ns 'raft.demo.api)))
