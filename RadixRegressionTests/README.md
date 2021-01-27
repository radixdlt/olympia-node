## Regression And Acceptance Test Suite

This project contains regression and acceptance tests for Radix Client API.

### Running Test Suite

The project contains two helper scripts which can be used to run tests.

- `run-docker-test.sh` - used to run Docker container where tests are executed
- `run-regression-test.sh` - used to run test suite locally

### Running Docker tests
(TODO: describe running docker tests)

### Running Test Suite Locally
In order to run test suite locally running Radix Network is necessary. It's recommended to have at least 2 nodes in the 
network. For development purposes such a network can be started using instructions provided in 
[radixdlt-core/docker/README.md](https://github.com/radixdlt/radixdlt-core/blob/release/1.0-beta.19/docker/README.md).
Once network is up and running, just launch `run-regression-test.sh` script.
