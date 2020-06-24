
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps
docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN  --name=test-executor radix-system-test ./gradlew clean dockerSystemTests
#docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN --name=test-executor radix-system-test tail -f /dev/null
#docker network connect DID-test test-executor
docker start -a test-executor
