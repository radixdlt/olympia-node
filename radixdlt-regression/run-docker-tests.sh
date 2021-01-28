#!/bin/bash
# This script represents commands that can be used to run tests locally on development laptop and also on jenkins
set -x
test_executor="${TEST_EXECUTOR:-test-executor}"
echo "Test executor Container Name = $test_executor"
echo "Duration of individual tests = $TEST_DURATION"

if [[  -z "${CORE_DIR}" ]]; then
  CORE_DIR=$(echo $(cd ../ && pwd))
  echo " CORE_DIR is ${CORE_DIR}"
fi

docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a

../gradlew clean
docker rm -f "${test_executor}" || true
# Currently there is volume mount consisting of core code, may need to use docker named volumes
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