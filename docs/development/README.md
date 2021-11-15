## Development Environment Setup

### Prerequisites
- Have the Java 11 SDK installed. If you have several versions installed, set your PATH to point at Java 11. 
- Use a recent verson of Linux or MacOS (Windows WSL2 may work, but it has not been tested)
- Git v2.27+
- Docker v20.10+
- Docker Compose v1.25+

### Getting code

* If you intend to contribute, fork the main repository https://github.com/radixdlt/radixdlt into your account and then clone it locally.
* Otherwise, just clone the main repo at https://github.com/radixdlt/radixdlt

### Building code
Use following command to build binaries and run unit tests:

```shell
$ ./gradlew clean build
```

### Running integration tests

Integration tests take quite a while to run (over an hour on most machines).

They are typically run as part of a PR.

```shell
$ ./gradlew integrationTest
```

### Run configurations

There are a variety of [run configurations](./run-configurations), depending on how you'd like to test your code:

* [Launching a local network in Docker](./run-configurations/launching-a-local-network-in-docker.md)
* [Connecting to a live network via Docker](./run-configurations/connecting-to-a-live-network-in-docker.md)
* Connecting to a live network without Docker
* Running with nginx in front of the node (to replicate a more production-like setup)