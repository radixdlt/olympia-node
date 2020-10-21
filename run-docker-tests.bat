:: This script represents commands that can be used to run tests locally on development laptop and also on jenkins
@echo off

if "%RADIXDLT_UNIVERSE%"== "" (
    echo RADIXDLT_UNIVERSE environment variable isn't setup. Exiting the tests
    EXIT /B 1
)

IF NOT DEFINED TEST_EXECUTOR SET "TEST_EXECUTOR=test-executor"
echo Test executor Container Name: "%TEST_EXECUTOR%"
echo Duration of individual tests: "%TEST_DURATION%"

docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps -a

docker rm -f %test_executor%
docker create  --pid=host --privileged ^
-v /var/run/docker.sock:/var/run/docker.sock ^
--network=host --cap-add=NET_ADMIN ^
-e CONTAINER_NAME -e TEST_DURATION -e RADIXDLT_UNIVERSE=%RADIXDLT_UNIVERSE% ^
--name=%TEST_EXECUTOR% radix-system-test ^
gradle clean dockerSystemTests
docker start -a %TEST_EXECUTOR%
docker cp %TEST_EXECUTOR%:src/system-tests .