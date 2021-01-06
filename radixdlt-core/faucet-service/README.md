# Faucet Service


A simple service which sends tokens to whoever sends a message to this service's
address.

To run the service:
```
> export FAUCET_TOKEN_RRI=<rri-of-token-to-issue>
> export FAUCET_IDENTITY_KEY=<base64-private-key-for-token-rri>
> export RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:8080
> java com.radixdlt.client.services.Faucet
```

## When creating/pushing docker image (not required right now)

### Create image (not required
```
make TAG=betanet faucet
```

### Push image
```
make TAG=betanet faucet-push
```
