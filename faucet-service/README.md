# Faucet Service


A simple service which sends tokens to whoever sends a message to this service's
address.

To run the service:
```
> export RADIX_BOOTSTRAP_TRUSTED_NODE=http://localhost:8080
> export RADIX_IDENTITY_KEY_FILE=/home/user/my.key
> export RADIX_IDENTITY_KEY_FILE_PASSWORD=password123
> export FAUCET_TOKEN_RRI=/9i3j1YsGd6z65oxyhuNbFL1LaDAkEmuqw9qm4LuWFUd2Gr1aqA4/XRD
> java com.radixdlt.client.services.Faucet
```

## Create image
```
make TAG=betanet faucet
```

## Push image
```
make TAG=betanet faucet-push
```