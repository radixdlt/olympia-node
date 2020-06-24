
docker build -f system-tests/docker/Dockerfile -t radix-system-test .
docker ps
docker network create DID-test || true
docker network create test-runner-network || true
docker rm -f test-executor || true
docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN  --name=test-executor radix-system-test ./gradlew clean dockerSystemTests --tests "com.radixdlt.test.SlowNodeTests.given_3_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made" --stacktrace
#docker create  --pid=host --privileged  -v /var/run/docker.sock:/var/run/docker.sock --network=host --cap-add=NET_ADMIN --name=test-executor radix-system-test tail -f /dev/null
#docker network connect DID-test test-executor
docker start -a test-executor
