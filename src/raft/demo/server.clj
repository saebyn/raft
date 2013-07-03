(ns raft.demo.server
  (:use raft.heartbeat)
  (:require [slacker.server :as slacker]))


(def raft-instance (atom nil))


(defn- rpc-server [rpc-ns rpc-address rpc-port]
  ; TODO only bind to the rpc-address
  (println "RPC server started")
  (future
    ; ugh, apparently, this puts itself into a thread and doesn't
    ; give us a handle for it :(
    (println (slacker/start-slacker-server rpc-ns rpc-port))
    (println "RPC server stopped")))


(defn- external-service-server []
  (println "External service server started")
  (future
    ; TODO
    (Thread/sleep 1000000)
    (println "External service server stopped")))


(defn- heartbeat-server [broadcast-time]
  (println "Heartbeat server started")
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
      (println "Heartbeat server stopped"))))


(defn- stats-server []
  (println "Stats server started")
  (future
    ; TODO
    (Thread/sleep 100000)
    (println "Stats server stopped")))


(defn run-server [rpc-ns rpc-address rpc-port broadcast-time]
  ; TODO logging
  (let [stopped (promise)
        operations [(partial rpc-server rpc-ns rpc-address rpc-port)
                    external-service-server
                    (partial heartbeat-server broadcast-time)
                    stats-server]
        operations (doall
                     (map (fn [f]
                            (future
                              (deliver stopped (deref (f))))) operations))]
    ; Wait until the first component stops, then quit.
    @stopped
    (println "Server stopping")
    (System/exit 0)))
