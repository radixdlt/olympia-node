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

Run a single test via tags:

```
../gradlew clean :radixdlt-regression:acceptanceTest -Dcucumber.filter.tags="@single"
```

By default, an archive node running at localhost is expected. You can change this with:
```
RADIXDLT_JSON_RPC_API_ROOT_URL=http://localhost:1234 \
RADIXDLT_JSON_RPC_API_PRIMARY_PORT=8080 \ 
RADIXDLT_JSON_RPC_API_SECONDARY_PORT=3333 \
../gradlew clean :radixdlt-regression:acceptanceTest
```
