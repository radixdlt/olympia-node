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

* `RADIXDLT_JSON_RPC_API_ROOT_URL`: The tests will use this JSON-RPC to communicate with the network e.g. submit transactions, query state.
* `RADIXDLT_JSON_RPC_API_PRIMARY_PORT`: Radix JSON-RPC APIs have two ports: A primary one for public functionality and a secondary one for internal ones. 
* `RADIXDLT_JSON_RPC_API_SECONDARY_PORT`: See above
* `RADIXDLT_BASIC_AUTH`: If your JSON-RPC API is using basic auth, set the credentials like username:password. 

Docker properties:

* RADIXDLT_DOCKER_DAEMON_URL: 
* RADIXDLT_DOCKER_CONTAINER_NAME:
* RADIXDLT_DOCKER_INITIALIZE_NETWORK: 
* RADIXDLT_DOCKER_IMAGE:
* RADIXDLT_DOCKER_INITIAL_NUMBER_OF_NODES:
* RADIXDLT_DOCKER_NETWORK_NAME: