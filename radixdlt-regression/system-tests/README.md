## System tests

### How to run

For mac or linux:
```
./gradlew systemTests 
```

This should start a local radix docker network, run the tests, and then remove the network & containers.

On Windows, you should expose the docker daemon on a local port. Then set its URL: 
```
set RADIXDLT_DOCKER_DAEMON_URL=tcp://localhost:2375
``` 

Run the gradle task:
```
gradlew.bat systemTests
```

#### Running a single test
```
./gradlew systemTests --tests "com.radixdlt.test.system.MyNewTest"
```

#### Running against a remote network

Do not create a local network with:
```
RADIXDLT_DOCKER_INITIALIZE_NETWORK=false
```
Then use the RADIXDLT_JSON_RPC_API_* properties to point to a Radix JSON-RPC API. 

### Properties

These are the environment variables that the test framework uses.

General properties:

* `RADIXDLT_JSON_RPC_API_ROOT_URL`: The JSON-RPC API of the network to test.
* `RADIXDLT_JSON_RPC_API_PRIMARY_PORT`: Radix JSON-RPC APIs have two ports: A primary one for public functionality and a secondary one for internal ones. 
* `RADIXDLT_JSON_RPC_API_SECONDARY_PORT`: See above.
* `RADIXDLT_BASIC_AUTH`: If your node is using basic auth, set the username:password here.

Docker properties:

* `RADIXDLT_DOCKER_DAEMON_URL`: The system tests use docker to create netowrks and manipulate containers. Set the non-TLS docker daemon URL of your docker installation here. 
* `RADIXDLT_DOCKER_CONTAINER_NAME`: The naming pattern of the containers to be created. Uses wildcard %d for the container number. 
* `RADIXDLT_DOCKER_INITIALIZE_NETWORK`: Will create a new radix network via docker, before running the tests. When running system tests, this is true by default.  
* `RADIXDLT_DOCKER_IMAGE`: The radix node image to be used, defaults to radixdlt/radixdlt-core:develop.
* `RADIXDLT_DOCKER_INITIAL_NUMBER_OF_NODES`: If a network is to be initialized, it will consist of this number of nodes.
* `RADIXDLT_DOCKER_NETWORK_NAME`: The name of the actual docker network created for system testing.