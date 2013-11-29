# raft-clj

A Clojure library that implements the Raft consensus algorithm.

See [In Search of an Understandable Consensus Algorithm](https://ramcloud.stanford.edu/wiki/download/attachments/11370504/raft.pdf) by Diego Ongaro and John Ousterhout for more information.


This is a work in progress.

See doc/intro.md for some additional notes.


## TODO

- Debug leader election problems

- For demo

  - If servers are provided, begin an election on start. Otherwise, stay as follower and wait for configuration change to add the node.

  - Add cluster config commands (add/remove nodes)

- Implement configuration change mechanism

- More/better documentation


## Usage

Requires [Leiningen](https://github.com/technomancy/leiningen).


The demo can be run with:

    $ lein run


See some information about command-line arguments:


    $ lein run -- --help



## License

Copyright © 2013 John Weaver

Distributed under the Eclipse Public License, the same as Clojure.
