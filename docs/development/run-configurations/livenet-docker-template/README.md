# Radix - Development Docker Node

This set of scripts runs a local docker image `radixdlt/radixdlt-core:develop` created from the main repo.

This requires docker-compose to be installed locally.

The node's files will be mounted under `./node/`.

## First time set-up

Before starting, ensure this template folder has been copied outside the radixdlt repository,
and given a sensible name describing the node you will run.

Then, start a terminal in the copied folder.

* Create and update `node-variables.sh`:
  * Run `cp node-variables.template.sh node-variables.sh`
  * Edit `node-variables.sh` to update the configuration inside.
* Run `chmod +x generate-key.sh` and `chmod +x start-node.sh`
* Run `./generate-key.sh` to generate a public/private keystore at `./node/keystore.ks`.

You are now prepared and ready to start the node - but we just need to build the correct image
that you wish the node to run from.

## Create a docker image of a development build

From the `radixdlt` repo, on the correct branch, run these commands in turn:

```bash
# Build the image
./gradlew deb4docker

# Check the image has been built - if this command returns nothing, look for errors with the previous command
ls -l ./radixdlt-core/docker/*.deb 

# Build and locally store the image.
# If successful, the last output should read something like "naming to docker.io/radixdlt/radixdlt-core:develop"
docker-compose -f radixdlt-core/docker/node-1.yml build
```

This builds a `radixdlt/radixdlt-core:develop` image and stores it locally under docker-compose's registry.

## Start node

Run `./start-node.sh` to start the most recently created version of the `radixdlt/radixdlt-core:develop` image through docker-compose,
using the configuration at `node-variables.sh`.

Some notes:
* You will need to call docker directly to stop the node when you're done (eg through docker desktop).
* The ledger will be mounted to `./node/ledger` by default.
* The node will start syncing whilst active, and the ledger may consume a lot of disk space over time.

### Checking your running node is running okay

You can test it's working with a few example requests:

Network Configuration:
```
curl --location --request POST 'localhost:3333/network/configuration' \
--header 'Content-Type: application/json' \
--data-raw '{}'
```

Network Status:
```
curl --location --request POST 'localhost:3333/network/status' \
--header 'Content-Type: application/json' \
--data-raw '{
    "network_identifier": { "network": "mainnet" }
}'
```

Transactions stream:
```
curl --location --request POST 'localhost:3333/transactions' \
--header 'Content-Type: application/json' \
--data-raw '{
    "network_identifier": { "network": "mainnet" },
    "state_identifier": { "state_version": 1 },
    "limit": 10
}'
```

## Clearing the ledger

Stop the node and delete the folder `./node/ledger`.