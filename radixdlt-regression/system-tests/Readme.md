### Running docker system tests
Some of Docker system tests need to manipulate docker network and support execution using docker container only.
To run docker system tests run below command on root directory

```shell script
./run-docker-tests.sh
``` 

### Running system tests against cluster
System tests against cluster need cluster information. If the cluster is configured using ansible, then tests fetch cluster information using ansible container named eu.gcr.io/lunar-arc-236318/node-ansible.
To run cluster tests  against testnet_2 ( the network configured in ansible hosts file)
```shell script
SSH_IDENTITY=<location of ssh key> TESTNET_NAME=testnet_2 ./gradlew clean clusterSystemTests
```
eu.gcr.io/lunar-arc-236318/node-ansible is in private repo . To loggin into private google docker repository use the information provided in 1password

If system tests need to be run against cluster that is not setup using ansible, then cluster information can be passed as a java system property -DclusterNodeUrls. However this only supports LatentNetworkTests at the moment.

```shell script
./gradlew clean clusterSystemTests --tests "com.radixdlt.test.LatentNetworkTest.given_4_correct_bfts_in_latent_cluster_network__then_all_instances_should_get_same_commits_and_progress_should_be_made"  -DclusterNodeUrls=https://3.11.89.232,https://3.11.217.19,https://35.179.58.27,https://3.11.140.100
``` 