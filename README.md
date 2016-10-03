# tix-time-server
[![Build Status](https://travis-ci.org/TiX-measurements/tix-time-server.svg?branch=master)](https://travis-ci.org/TiX-measurements/tix-time-server)
[![codecov](https://codecov.io/gh/TiX-measurements/tix-time-server/branch/master/graph/badge.svg)](https://codecov.io/gh/TiX-measurements/tix-time-server)

TiX Time Server is the application that runs on the server side. It receives the packets from each client, timestamps it
and sends it back. It also recollects the data packages from the client to process them in the next instance of the
system.
