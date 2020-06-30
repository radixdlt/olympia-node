#!/bin/bash
# This script represents commands that can be used to run tests locally on development laptop and also on jenkins
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a
docker rm -f test-executor
docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN  --name=test-executor radix-system-test ./gradlew clean dockerSystemTests
docker start -a test-executor
