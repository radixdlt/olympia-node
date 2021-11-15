# Core API

The Core API provides endpoints for node management and integration support.

The OpenAPI 3.1.0 specification be found [here](api.yaml). If 3.0.x is required, the version can simply be downgraded.

Documentation can be found [here](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/radixdlt-core/radixdlt/src/main/java/com/radixdlt/api/core/api.yaml).

## Running a Node

The Core API is currently on beta and not yet released. To develop
against these new APIs, you will need to checkout the `feature/open-api` branch
and [run a node](../../../../../../../../../docs/development) connected to a live
network such as stokenet or mainnet.

## Version Notes

The current version 0.9.0 of the Core API is a preview of the API
intended to allow integrators to begin integrating the Radix ledger.
In particular the following endpoints will be backwards compatible
once 1.0.0 is released:

* `/network/configuration`
* `/network/status`
* `/transactions`
* `/construction/derive`
* `/construction/build`
* `/construction/parse`
* `/construction/finalize`
* `/construction/hash`
* `/construction/submit`
 
All other endpoints may change by the 1.0.0 release, and further endpoints (such as mempool contents) will be added.