## Acceptance Test Suite

### Run locally

Start a local network: 
```
../radixdlt-core/docker/scripts/rundocker.sh
```

Run the tests:
```
../gradlew clean :radixdlt-regression:acceptanceTest
```

By default, the tests will run again an archive node at localhost:8080|3333. You can change this with:
```
RADIXDLT_JSON_RPC_API_ROOT_URL=http://my-other-archive-node \
RADIXDLT_JSON_RPC_API_PRIMARY_PORT=8080 \ 
RADIXDLT_JSON_RPC_API_SECONDARY_PORT=3333 \
../gradlew clean :radixdlt-regression:acceptanceTest
```

You can also run the tests against a testnet:
```
RADIXDLT_FAUCET_URL=https://rcnet-faucet.radixdlt.com \
RADIXDLT_JSON_RPC_API_ROOT_URL=https://rcnet.radixdlt.com \
../gradlew clean :radixdlt-regression:acceptanceTest 
```

Run a single test via tags:

```
../gradlew clean :radixdlt-regression:acceptanceTest -Dcucumber.filter.tags="@single"
```


