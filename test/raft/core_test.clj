(ns raft.core-test
  (:use midje.sweet)
  (:require [raft.core :refer :all]))


(facts "about core"
  (fact (+ 1 1) => 3))
