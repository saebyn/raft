(ns raft.log-test
  (:use midje.sweet)
  (:require [raft.log :refer :all]))


(facts "about log"
  (fact (+ 1 1) => 3))
