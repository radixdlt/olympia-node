# Launching a local network in docker

This run configuration is useful for testing that your code can run a network.

You can choose how many nodes to run with (typically 1 to 5), and the script
builds the node docker image, creates a new localnet genesis transaction for the right number
of validators, and spins up the right number of validator docker images via docker compose.

__NOTE:__ At present, validators create new consensus rounds as fast as latency allows, so on a local
network, this can be quite a CPU hog.

See [the README in the docker folder](/docker) for more detailed documentation.

### Launching local network

From the repo root, run the following command:

```shell
$ ./docker/scripts/rundocker.sh <number-of-nodes>
```

Where `<number-of-nodes>` is any value between 2 and 5. This value defined number
of validators in the network. Each validator has enabled all endpoints by default.

### Launching a local network for more than 5 nodes

If you want to launch network with more than 5 nodes, then you can generate necessary
docker-compose configuration file using dedicated script - `generate-yml.sh`.

For example, following command generates configuration file for network with 15 nodes:
```shell
$ ./docker/scripts/generate-yml.sh -n 15 -p 8080
```
For more information about this script, run it without parameters.

__WARNING__: Each node consumes considerable resources, so running large network requires quite capable hardware.
