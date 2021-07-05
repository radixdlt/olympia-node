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
```shell
$ ./gradlew integrationTest
```

### Launching local network

Change directory to `radixdlt-core/docker`. Then run following command:

```shell
$ ./scripts/rundocker.sh <number-of-nodes>
```

Where `<number-of-nodes>` is any value between 2 and 5. This value defined number 
of validators in the network. Each validator has enabled all endpoints by default.

### Launching local network for more than 5 nodes

If you want to launch network with more than 5 nodes, then you can generate necessary
docker-compose configuration file using dedicated script - `generate-yml.sh`. 

For example, following command generates configuration file for network with 15 nodes:
```shell
$ ./scripts/generate-yml.sh -n 15 -p 8080
```
For more information about this script, run it without parameters.

__WARNING__: Each node consumes considerable resources, so running large network requires quite capable hardware.

### IntelliJ IDEA Troubleshooting
In some cases IntelliJ IDEA may deny to load project properly. Usually this happens if you have installed more than one Java version.
If you meet this issue, check following configuration options:
 - `Project Structure -> Project Settings -> Project`, make sure `Project SDK` and `Project Language Level` is set to Java 11.
 - `Project Structure -> Project Settings -> Modules`, make sure that every module has `Language Level` set to Java 11 (`Project default`)  
 - `Settings -> Build,Execution, Deployment -> Build Tools -> Gradle`, make sure that `Gradle JVM` is set to `Project JDK`. 

Once you have all settings fixed, force reloading of the Gradle configuration (you may ignore `jmh` dependency errors) and then rebuild project.

