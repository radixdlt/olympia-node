## Development Environment Setup

### Prerequisites
- Java 11 SDK installed. If you have several versions installed, it is preferred to have Java 11 set as default version. 
- more or less recent Linux or MacOS (Windows WSL2 may work, but not tested)
- git 2.27+
- docker version 20.10+
- docker-compose version 1.25+

Two last prerequisites are necessary only if you plan to launch local network. 

### Getting code
There are two options, depending on the need/desire to contribute.

#### You plan to contribute
In this case you need to fork the main repository https://github.com/radixdlt/radixdlt into your account and then clone it locally.

#### You don't plan to contribute
Just clone the main repo at https://github.com/radixdlt/radixdlt

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