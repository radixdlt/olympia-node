#!/bin/bash
# This script represents commands that can be used to run tests locally on development laptop and also on jenkins
set -x
test_executor="${TEST_EXECUTOR:-test-executor}"
echo "Test executor Container Name = $test_executor"
echo "Duration of individual tests = $TEST_DURATION"

if [[  -z "${CORE_DIR}" ]]; then
  echo " CORE_DIR environment variable isn't setup. Exiting the tests"
  exit 1
fi

docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a

docker rm -f "${test_executor}" || true
docker create  --pid=host --privileged  \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v ${CORE_DIR}:/core \
      --network=host --cap-add=NET_ADMIN  \
      -e CONTAINER_NAME -e TEST_DURATION -e CORE_DIR=/core \
      --name=${test_executor} radix-system-test \
      ./gradlew clean dockerSystemTests --refresh-dependencies --info
docker start -a "${test_executor}"
docker cp $test_executor:src/system-tests .
test_status=$(docker inspect $test_executor --format='{{.State.ExitCode}}')
exit $test_status