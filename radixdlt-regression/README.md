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

By default, an archive node running at localhost:8080 is expected. You can change this with:
```
RADIX_JSON_RPC_ROOT_URL=http://localhost:1234 \
../gradlew clean :radixdlt-regression:acceptanceTest
```
