(ns raft.demo.core
  (gen-class)
  ; Use midje.sweet to prevent complaints about use of defrecord-openly
  ; in raft.core.
  (:use midje.sweet clj-logging-config.log4j)
  (:require [raft.demo.server :refer [raft-instance run-server]]
            [clojure.string :as string]
            [clojure.tools.logging :as l]
            [clojure.core.async :refer [thread]]
            [raft.core :refer [create-raft]]
            [zeromq [zmq :as zmq]]
            [taoensso.nippy :as nippy]
            [clojure.tools.cli :refer [parse-opts]]))


(set-logger! :level :debug)
(alter-var-root #'include-midje-checks (constantly false))


(def connections (atom {}))


; TODO better error handling when nonsense gets here
(defn- connect-server
  "Connect to server using ZeroMQ"
  [zmq-context server]
  [server (doto (zmq/socket zmq-context :req)
            (zmq/connect server))])


; TODO implement an actual storage mechanism
; cupboard?
(defn- fake-storage
  "Dummy storage function."
  ([k]
   (l/debug "Fetching key" k "from storage")
   nil)
  ([k v]
   (l/debug "Setting key" k "to storage")
   nil))


(defn- fake-state-machine
  "Dummy state machine."
  [command]
  (l/debug "Got FSM input" command)
  [nil fake-state-machine])


(defn- rpc
  "Make an remote procedure call to a raft server node.
   Returns a channel that receives the result."
  [server command & args]
  (thread
    ; TODO XXX this is very basic/naive
    ; needs:
    ;  timeout/abort/reset socket on failure
    ;  some number of retries
    (let [conn (@connections server)]
      (l/debug "sending RPC via zmq to" server)
      (zmq/send conn (nippy/freeze [command args]))
      (let [resp (nippy/thaw (zmq/receive conn))]
        (l/debug "got RPC response via zmq from" server)
        resp))))


; TODO will need DB setup params for storage mechanism
(def ^:private start-raft-options
  [["-h" "--help" "Show this help"
    :default false :flag true]
   ["-A" "--server-address ADDR" "Endpoint to listen for incoming Raft RPC"
    :default "tcp://localhost:2104"]
   ["-X" "--api-address ADDR" "Endpoint to listen for external API requests"
    :default "tcp://localhost:2105"]
   ["-e" "--election-timeout N" "Minimum election timeout, in milliseconds"
    :default "150"]
   ["-b" "--broadcast-time N" "Time between heartbeat messages, in milliseconds"
    :default "15"]])


(def ^:private send-command-options
  [])


(def ^:private main-options
  [])


(defn- start-raft-usage [summary]
  (str "Usage: program-name start [options]"
       " <list of other raft servers (e.g. tcp://example.com:2104)>"
       "
       
       Options:
       \n" summary "
       "))


(defn- send-command-usage [summary]
  (str "Usage: program-name send [options]"
       " <raft server (e.g. tcp://example.com:2104)>"
       " <message>
       
       Options:
       \n" summary "
       "))


(defn- main-usage [summary]
  (str "Usage: program-name [options] command [command options]
       
        Options:
        \n" summary "
       
        Commands:

          start    Start raft node
          send     Send command to a raft node
       "))


(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))


(defn exit  [status msg]
  (println msg)
  (System/exit status))


(defn- start-raft
  "Start a raft instance."
  [main-options args]
  (let [{:keys [options arguments errors summary]} (parse-opts args start-raft-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (start-raft-usage summary))
      errors (exit 1 (error-msg errors)))

    (let [this-server (:server-address options)
          this-external-server (:api-address options)
          servers arguments
          timeout (Integer/parseInt (:election-timeout options))
          broadcast-time (Integer/parseInt (:broadcast-time options))
          zmq-context (zmq/context)
          raft (create-raft
                rpc fake-storage fake-state-machine
                this-server servers
                :election-timeout timeout)
          server-connections (into {}
                                   (map (partial connect-server zmq-context)
                                        servers))]
    (reset! raft-instance raft)
    (reset! connections server-connections)
    (run-server zmq-context this-server this-external-server broadcast-time))))


(defn- send-command
  "Send a command to a raft instance."
  [main-options args]
  (let [{:keys [options arguments errors summary]} (parse-opts args send-command-options)]
  ;; Handle help and error conditions
  (cond
    (:help options) (exit 0 (send-command-usage summary))
    (not= (count arguments) 2) (exit 1 (send-command-usage summary))
    errors (exit 1 (error-msg errors)))

  (println "TODO" main-options args options)))


(defn -main
  "Raft demo server."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args main-options :in-order true)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (main-usage summary))
      (< (count arguments) 1) (exit 1 (main-usage summary))
      errors (exit 1 (error-msg errors)))

    ;; Execute program with options
    (case (first arguments)
      "start" (start-raft options (rest arguments))
      "send" (send-command options (rest arguments))
      (exit 1 (main-usage summary)))))
