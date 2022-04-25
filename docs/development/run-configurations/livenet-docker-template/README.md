# Radix - Development Docker Node

This set of scripts runs a local docker image `radixdlt/radixdlt-core:main` created from the main repo.

This requires docker-compose to be installed locally.

The node's files will be mounted under `./node/`.

To be clear, this is _not_ the recommended set-up for a production node - which is given at
[https://docs.radixdlt.com](https://docs.radixdlt.com/main/node/introduction.html). In particular,
we are not setting up nginx, and not allowing inbound connections to our node.

## First time set-up

Before starting, ensure this template folder has been copied outside the radixdlt repository,
and given a sensible name describing the node you will run.

Then, start a terminal in the copied folder, and run:

```sh
cp template.env .env
chmod +x generate-key.sh
chmod +x start-node.sh
chmod +x stop-node.sh
```

Now, edit the `.env` file to fix a configuration, including setting up the `RADIXDLT_NODE_KEY_PASSWORD`
which will be used by the key generation.

Then, to generate the keys for your node, run:

```sh
./generate-key.sh  # This generates a public/private keystore at node/keystore.ks
```

You are now prepared and ready to start the node - but we just need to build the correct image
that you wish the node to run from.

## Create a docker image of a development build

From the `radixdlt` repo, on the correct branch, run these commands in turn:

```bash
# Build the image
./gradlew deb4docker

# Check the image has been built - if this command returns nothing, look for errors with the previous command
ls -l ./docker/*.deb 

# Build and locally store the image.
# If successful, the last output should read something like "naming to docker.io/radixdlt/radixdlt-core:main"
docker-compose -f docker/node-1.yml build
```

This builds a `radixdlt/radixdlt-core:main` image and stores it locally under docker-compose's registry.

## Start node

Run `./start-node.sh` to start the most recently created version of the `radixdlt/radixdlt-core:main` image through docker-compose,
using the configuration at `node-variables.sh`.

Some notes:
* The ledger will be mounted to `./node/ledger` by default.
* The node will start syncing whilst active, and the ledger may consume a lot of disk space over time.
* We run docker-compose with the -d flag, so the container will need to be shut down manually 
  when you wish to shut it down. Ctrl-C out of the logs and run `./stop-node.sh` to shut it down.
* If for some reason the node stops, the docker-compose file is set to restart it.
* The ledger (and logs) persist across starts/stops, in the `./node` directory.
  If you wish to clear the ledger, stop the node with `./stop-node.sh` and run `./clear-ledger.sh`

### Checking your running node is running okay

You can test it's working with a few example requests from a different terminal:

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

### Debugging

If your node is crashing a lot, this is possibly because Docker Desktop is killing it because it's using too
much memory - it needs >2GB, something more like 5GB should be enough.

Check out this stack overflow post: https://stackoverflow.com/a/50770267
