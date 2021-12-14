## Development Environment Setup

- Java 17 SDK installed. If you have several versions installed, it is preferred to have Java 17 set as default version. 
- more or less recent Linux or MacOS (Windows WSL2 may work, but not tested)
- git 2.27+
- docker version 20.10+
- docker-compose version 1.25+

Two last prerequisites are necessary only if you plan to launch local network. 

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

### IntelliJ IDEA Troubleshooting
In some cases IntelliJ IDEA may deny to load project properly. Usually this happens if you have installed more than one Java version.
If you meet this issue, check following configuration options:
 - `Project Structure -> Project Settings -> Project`, make sure `Project SDK` and `Project Language Level` is set to `17 (Preview) - Pattern matching for switch`.
 - `Project Structure -> Project Settings -> Modules`, make sure that every module has `Language Level` set to `17 (Preview) - Pattern matching for switch (Project default)`  
 - `Settings -> Build,Execution, Deployment -> Build Tools -> Gradle`, make sure that `Gradle JVM` is set to `Project JDK`. 

There are a variety of [run configurations](./run-configurations), depending on how you'd like to test your code:

* [Launching a local network in Docker](./run-configurations/launching-a-local-network-in-docker.md)
* [Connecting to a live network via Docker](./run-configurations/connecting-to-a-live-network-in-docker.md)
* Connecting to a live network without Docker
* Running with nginx in front of the node (to replicate a more production-like setup)