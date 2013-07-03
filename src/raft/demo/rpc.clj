(ns raft.demo.rpc
  (:require [raft.demo.server :as server]
            [raft.log :as log]
            [raft.vote :as vote]))


; TODO grab raft-instance atom from server, call our local
; methods, swap the atom, return the result of the local
; method call.

(defn append-entries [& args]
  (println "append-entries" args)
  ; TODO
  {:term -1 :success false})


(defn request-vote [& args]
  (println "request-vote" args)
  ; TODO
  {:term -1 :vote-granted false})
