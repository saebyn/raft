(ns raft.demo.server
  (:require [slacker.server :as slacker]))


(def raft-instance (atom nil))


(defn run-server [api-ns]
  ; 
  ; So... we want to have a few threads going on
  ;
  ; we'll have an atom containing our raft
  ; then we'll have a thread with the rpc server
  ; then we'll have a thread with the heartbeat check + elapsed time
  ; then we'll have a thread dumping stats on the raft on occasion
  ; and maybe more later
  ;
  (slacker/start-slacker-server api-ns 2104)
  (comment
  (let [start (System/currentTimeMillis)]
     (-> raft
       heartbeat
       (decrease-election-timeout
         (- start (System/currentTimeMillis)))))))
