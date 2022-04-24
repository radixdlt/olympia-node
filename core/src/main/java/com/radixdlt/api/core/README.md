# Core API

The Core API provides endpoints for node management and integration support.

The OpenAPI 3.1.0 specification be found [here](api.yaml). If 3.0.x is required, the version can simply be downgraded.

Documentation can be found [here](https://redocly.github.io/redoc/?url=https://raw.githubusercontent.com/radixdlt/radixdlt/feature/open-api/core/src/main/java/com/radixdlt/api/core/api.yaml).

The Core API runs by default on port 3333. __The Core API should NOT be publicly exposed__.

## Running a Node

The Core API is currently on beta and not yet released. To develop
against these new APIs, you will need to checkout the `feature/open-api` branch
and [run a node](../../../../../../../../../../docs/development) connected to a live
network such as stokenet or mainnet.

## Version Notes

All endpoints in Version 0.9.0 are intended to be backwards compatible
with version 1.0.0 once released so that there is little risk that clients
working with this spec will break once 1.0.0 is released. Additional endpoints
(such as retrieving mempool contents) are planned to be added.
