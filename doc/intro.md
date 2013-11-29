# Introduction to raft-clj

TODO: write [great documentation](http://jacobian.org/writing/great-documentation/what-to-write/)


## How the demo works (temp notes for later) - it doesn't yet

'demo.server.run-server is a function that runs the individual server components

* an RPC server for receiving remote procedure calls from other raft nodes

* a "external service" server for receiving remote procedure calls from the clients of the raft cluster

* a heartbeat server that emits the heartbeat for the raft algorithm

* a statistics collection server for node and cluster diagnostics

Each of these individual server component functions evaluates to a future
that resolves when the component stops.


'demo.server.raft-instance is an atom containing the raft-related state of this raft node

The 'demo.core namespace contains our -main function. It (TODO) parses command-line arguments,
creates the raft state, puts the raft state into the 'demo.server.raft-instance atom, and
starts up the server via 'demo.server.run-server.


## Demo cli parameters

broadcastTime ≪ electionTimeout ≪ MTBF


TODO explain how to start a cluster of demo instances

TODO explain how to add additional instances to an existing cluster

TODO document how to build an app using the raft-clj library
