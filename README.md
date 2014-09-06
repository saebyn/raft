# raft-clj

A Clojure library that implements the Raft consensus algorithm.

See [In Search of an Understandable Consensus Algorithm](https://ramcloud.stanford.edu/wiki/download/attachments/11370504/raft.pdf) [PDF] by Diego Ongaro and John Ousterhout for more information.


*This is a work in progress.*

[![Build Status](https://travis-ci.org/saebyn/raft.svg?branch=master)](https://travis-ci.org/saebyn/raft)

*It doesn't work yet.* I'm happy to review pull requests and I am likely to accept
those with passing tests and that [have some style](https://github.com/bbatsov/clojure-style-guide).


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


You can run the demo with Leiningen:

    $ lein run


You can see some information about command-line arguments:


    $ lein run -- --help


You can run the test suite using Leiningen:

    $ lein midje



## License

Copyright © 2013-2014 John Weaver

Distributed under the Eclipse Public License, the same as Clojure.
