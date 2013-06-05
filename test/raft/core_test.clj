(ns raft.core-test
  (:use midje.sweet)
  (:require [raft.core :refer :all])
  (:import [raft.core Raft]))


(facts "about core"
       (fact "create-raft constructs a Raft record"
             (create-raft ..rpc.. ..store.. ..state-machine.. [..server..]) => (Raft. ..rpc.. ..store.. [] 0 {..server.. {}} 150 ..state-machine.. :follower nil nil nil)))
