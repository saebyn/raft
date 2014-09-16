# raft-clj

A Clojure library that implements the Raft consensus algorithm.

See [In Search of an Understandable Consensus Algorithm](https://ramcloud.stanford.edu/wiki/download/attachments/11370504/raft.pdf) [PDF] by Diego Ongaro and John Ousterhout for more information.


*This is a work in progress.*

[![Build Status](https://travis-ci.org/saebyn/raft.svg?branch=master)](https://travis-ci.org/saebyn/raft)

*It doesn't work yet.* I'm happy to review pull requests and I am likely to accept
those with passing tests and that [have some style](https://github.com/bbatsov/clojure-style-guide).


See doc/intro.md for some additional notes.


## TODO

- For demo

  - Add send command command

  - Add cluster config commands (add/remove nodes)

- Implement configuration change mechanism (major missing capability)

- More/better documentation

- Use tools.trace rather than the ugly things I'm doing now in various places
  to log out what's happening (but still using tools.logging for output).

- Refactor raft.leader and raft.log functions

  - Poorly documented

  - Overly long


## Usage

Requires [Leiningen](https://github.com/technomancy/leiningen).


You can run the demo with Leiningen:

    $ lein run start


You can run the demo with multiple nodes:

    $ lein run -- start -A tcp://localhost:2104 -X tcp://localhost:2105 tcp://localhost:2106 tcp://localhost:2108
    $ lein run -- start -A tcp://localhost:2106 -X tcp://localhost:2107 tcp://localhost:2104 tcp://localhost:2108
    $ lein run -- start -A tcp://localhost:2108 -X tcp://localhost:2109 tcp://localhost:2104 tcp://localhost:2106


(Coming soon)
You can send a command to a node:

    $ lein run -- send tcp://localhost:2105 my_command


You can see some information about command-line arguments:


    $ lein run -- --help


You can run the test suite using Leiningen:

    $ lein midje



## License

Copyright © 2013-2014 John Weaver

Distributed under the Eclipse Public License, the same as Clojure.
