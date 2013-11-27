(ns raft.demo.core
  (gen-class)
  (:use clojure.tools.logging clj-logging-config.log4j)
  (:require [raft.demo.server :refer [raft-instance run-server]]
            [raft.core :refer [create-raft]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]
            [clojure.tools.cli :refer [cli]]))


(set-logger!)


(def connections (atom {}))


(defn- connect-server
  "Connect to server using ZeroMQ"
  [zmq-context server]
  [server (doto (zmq/socket zmq-context :req)
            (zmq/connect server))])


; TODO implement an actual storage mechanism
(defn- fake-storage
  "Dummy storage function."
  ([k]
   (debug "Fetching key" k "from storage")
   nil)
  ([k v]
   (debug "Setting key" k "to storage")
   nil))


(defn- fake-state-machine
  "Dummy state machine."
  [command]
  (debug "Got FSM input" command)
  [nil fake-state-machine])


(defn- rpc
  "Make an remote procedure call to a raft server node."
  [server command & args]
  (debug "Sending RPC" command "to" server "with args" args)
  (future
    ; TODO XXX this is very basic/naive
    ; needs:
    ;  timeout/abort/reset socket on failure
    ;  some number of retries
    (zmq/send (@connections server) (nippy/freeze command args))
    (nippy/thaw (zmq/receive-all))))



(defn -main
  "Raft demo server."
  [& args]
  (info "Starting raft demo application")
  ; TODO will need DB setup params for storage mechanism
  (let [[options args banner] (cli args "Usage: <command> [args] [list of other raft servers (e.g. tcp://example.com:2104)]"
                                   ["-h" "--help" "Show this help" :default false :flag true]
                                   ["-A" "--server-address" "Endpoint to listen for incoming Raft RPC" :default "tcp://localhost:2104"]
                                   ["-X" "--api-address" "Endpoint to listen for external API requests" :default "tcp://localhost:2105"]
                                   ["-e" "--election-timeout" "Minimum election timeout, in milliseconds" :default "150"]
                                   ["-b" "--broadcast-time" "Time between heartbeat messages, in milliseconds" :default "15"])]
    (when (:help options)
      (println banner)
      (System/exit 0))
    (let [this-server (:server-address options)
          this-external-server (:api-address options)
          servers args
          timeout (Integer/parseInt (:election-timeout options))
          broadcast-time (Integer/parseInt (:broadcast-time options))
          zmq-context (zmq/context)
          raft (create-raft
                rpc fake-storage fake-state-machine
                this-server servers
                :election-timeout timeout)
          server-connections (into {} (map (partial connect-server zmq-context) servers))]
      (reset! raft-instance raft)
      (reset! connections server-connections)
      (run-server zmq-context this-server this-external-server broadcast-time))))
