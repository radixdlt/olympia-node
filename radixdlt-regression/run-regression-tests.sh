#!/bin/bash
# Run tests locally. Notice that you also need running network. The radixdlt-core project, contains necessary scripts in docker/scripts subdirectory.

RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:8080 ./gradlew clean test --info --stacktrace 
