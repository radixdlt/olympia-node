#!/bin/bash
# This script represents commands that can be used to run tests locally on development laptop and also on jenkins
set -x
test_executor="${TEST_EXECUTOR:-test-executor}"
export EXECUTOR=test_executor
echo "Test executor Container Name = $test_executor"
echo "Duration of individual tests = $TEST_DURATION"

if [[  -z "${RADIXDLT_UNIVERSE}" ]]; then
  echo " RADIXDLT_UNIVERSE environment variable isn't setup. Exiting the tests"
  exit 1
fi
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a
docker network create ${test_executor}
docker rm -f "${test_executor}" || true
docker create  --pid=host --privileged  \
      -v /var/run/docker.sock:/var/run/docker.sock \
      --cap-add=NET_ADMIN  \
      -e CONTAINER_NAME -e TEST_DURATION -e RADIXDLT_UNIVERSE=${RADIXDLT_UNIVERSE} \
      -e TEST_NETWORK=${test_executor} \
      --network ${test_executor} \
      --name=${test_executor} radix-system-test \
      ./gradlew clean dockerSystemTests --refresh-dependencies  --tests "com.radixdlt.test.SlowNodeTest"
docker start -a "${test_executor}"
docker cp $test_executor:src/system-tests .
test_status=$(docker inspect $test_executor --format='{{.State.ExitCode}}')
docker network rm ${test_executor}
exit $test_status