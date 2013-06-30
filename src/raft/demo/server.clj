(ns raft.demo.server
  (:require [slacker.server :as slacker]))


(def raft-instance (atom nil))


(defn- rpc-server [api-ns rpc-address rpc-port]
  ; TODO only bind to the rpc-address
  (println "RPC server started")
  (future
    (slacker/start-slacker-server api-ns rpc-port)))


(defn- heartbeat-server []
  (println "Heartbeat server started")
  (future nil))


(defn- stats-server []
  (println "Stats server started")
  (future nil))


(defn run-server [api-ns rpc-address rpc-port]
  ; TODO logging
  (let [stopped (promise)
        operations [(partial rpc-server api-ns rpc-address rpc-port)
                    heartbeat-server
                    stats-server]
        operations (doall
                     (map (fn [f]
                            (future
                              (deliver stopped (deref (f))))) operations))]
    ; Wait until the first component stops, then quit.
    @stopped
    (println "Server stopping")
    (System/exit 0))
  (comment
    (let [start (System/currentTimeMillis)]
      (-> raft
        heartbeat
        (decrease-election-timeout
          (- start (System/currentTimeMillis)))))))
