#!/bin/bash
# This script represents commands that can be used to run tests locally on development laptop and also on jenkins
#container_name="${JOB_BASE_NAME:-test-executor}-${BUILD_NUMBER:-0}"
container_name="${TEST_EXECUTOR:-test-executor}"
echo "Test executor Container Name = $container_name"
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a
docker rm -f "${container_name}"
docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN  -e CONTAINER_NAME --name=${container_name} radix-system-test ./gradlew clean dockerSystemTests
docker start -a "${container_name}"
