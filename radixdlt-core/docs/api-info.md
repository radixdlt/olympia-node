
## Current state of APIs as of May 10, 2021
<details>
  <summary>Click to see detailed information about endpoints and configuration</summary>

### REST APIs

 | Path | Method | Description | Comments |
 | --- | --- | --- | --- |
 | /chaos/message-flooder | PUT | message flooder | Necessary for testnets only |
 | /chaos/mempool-filler | PUT | mempool flooder |  Necessary for testnets only |
 | /faucet/request | POST | faucet API |  Necessary for testnets and betanet only |
 | /node/parse | POST | Transaction blob parsing |  No sensitive information |
 | /node/txn | POST | Retrieve transaction blob |  No sensitive information |
 | /node/submit | POST | Submit transaction blob |  No sensitive information |
 | /node/execute | POST | Submit transaction using node private key to for signature | __Security sensitive__ |
 | /node | GET | Get information about node - public address and balance | No sensitive information |
 | /node/validator | POST | Get information about node as a validator - stakes, registration status, etc.| No sensitive information |
 | /system/config | GET | Get active configuration parameters for consensus, mempool and RE | No sensitive information |
 | /system/info | GET | Get system information - public key, agent, supported protocols | No sensitive information |
 | /system/checkpoints | GET | Get genesis txn and proof | No sensitive information |
 | /system/proof | GET | Get current proof | No sensitive information |
 | /system/epochproof | GET | Get epoch proof | No sensitive information |
 | /system/peers | GET | Get information about peer nodes | No sensitive information |
 | /universe.json | GET | Get Radix Universe | No sensitive information |

### JSON-RPC APIs

| Method | Description |
| --- | --- |
| radix.nativeToken | Information about native token |
| radix.tokenInfo | Information about specified token |
| radix.tokenBalances | Token balances at specified account |
| radix.stakePositions | Stakes made from specified account |
| radix.unstakePositions | Pending unstakes for specified account|
| radix.transactionHistory | Transaction history for specified account |
| radix.lookupTransaction | Lookup specified transaction |
| radix.statusOfTransaction | Status of specified transaction |
| radix.buildTransaction | Assemble transaction given specified actions and optional message |
| radix.finalizeTransaction | Send signed transaction and receive transaction ID|
| radix.submitTransaction | Submit signed transaction for processing by network|
| radix.validators | Get list of validators |
| radix.lookupValidator | Get information about specified validator |
| radix.networkId | Get network ID |
| radix.networkTransactionThroughput | Get number of transactions per second |
| radix.networkTransactionDemand | Get average number of transactions waiting for processing in mempool |

### Configuration Options

Following configuration options control which APIs are enabled at the node:

| Configuration Option | Description |
| --- | --- |
| client_api.enable | Enables JSON-RPC APIs|
| universe_api.enable | Enables `/universe.json` API |
| faucet.enable | Enables `/faucet/request` API |
| chaos.enable | Enables `/chaos/message-flooder` and `/chaos/mempool-filler ` APIs |

</details>

## New Organization of APIs

 The general approach to the API organization is based on the following considerations:
- All methods are spread across four top-level routes:
  - `/archive` (former `/rpc`) - read-only methods, requiring an archive node
  - `/construct` - methods which support building and submitting transactions, which can be enabled by either archive or full nodes
  - `/system` - read-only methods which provide same information as available today via all `/system/*`
    and most `/node/*` endpoints
  - `/account` supports methods to fetch your associated account info, and a one-step method to build, sign, and submit a transaction
- The `/system` and `/account` endpoint are expected to be protected by firewall and/or require authentication/etc. (same requirements/setup as we have today)
- The `/system` endpoint is **enabled** by default
- The `/account` endpoint is disabled by default
- The `/archive` endpoint is disabled by default
- The `/construct` endpoint is disabled by default

### REST APIs

Majority of the REST APIs are replaced with JSON-RPC counterparts. Remaining and new REST endpoints:

| Path | Method | Description |
| --- | --- | --- |
| /health | GET | Returns node health status - `UP` or `SYNCING`. Standard endpoint for use by operations. Typical response: `{"status" : "UP" }` |
| /version | GET | Returns detailed information about software version and build info |
| /faucet/request | POST | faucet API - betanet and local deployment only, disabled on mainnet |
| /universe.json | GET | Get Radix Universe. Used during setup and configuration of the node |

### JSON-RPC APIs

#### /archive
| Method Name | Old Method Name |
| --- | --- |
| token.native | radix.nativeToken |
| token.info | radix.tokenInfo |
| address.balances | radix.tokenBalances |
| address.stakes | radix.stakePositions |
| address.unstakes | radix.unstakePositions |
| address.transactions | radix.transactionHistory |
| transaction.info | radix.lookupTransaction |
| transaction.status | radix.statusOfTransaction |
| validator.list | radix.validators |
| validator.info | radix.lookupValidator |
| network.id | radix.networkId |
| network.throughput | radix.networkTransactionThroughput |
| network.demand | radix.networkTransactionDemand |

#### /construct
| Method Name | Old Method Name | Notes |
| --- | --- | --- |
| transaction.build | radix.buildTransaction | Same as before |
| transaction.finalize | radix.finalizeTransaction | Now returns a fully ready-to-submit blob and a transaction ID |
| transaction.submit | radix.submitTransaction | Now accepts a single parameter, a ready-to-submit blob |

#### /system

| Method | Description |
| --- | --- |
| system.me | Node ID, validator registration status, (possible?) whether you are currently in the validator set |
| system.info | Complete information about system - consensus, mempool and RE configuration, public key, agent, protocols, genesis info, current and epoch proof |
| system.peers | Information about known peer nodes |

#### /account

| Method | Description |
| --- | --- |
| account.info | Your account's address and balances (not node ID) |
| transaction.submitSigned | Equivalent to `transaction.build + transaction.finalize + transaction.submit` methods except does not require keys and signs transaction with node private key. Input parameters are same as for `transaction.build`, output is formatted as for `transaction.submit` method |

### New Actions
In order to make JSON RPC API complete, we need to support following actions while building transactions:

| Action | Description |
| --- | --- |
| BurnTokens | Burn tokens |
| MintTokens | Mint tokens |
| RegisterValidator | Register node as validator |
| UnregisterValidator | Unregister node as validator |
| CreateFixedSupplyToken | Create fixed supply token |
| CreateMutableSupplyToken | Create mutable supply token |

### Removal of `/node` REST endpoints
Note that all of the wallet-style actions which were previously done via REST under `/node` are now supported via Actions under the normal transaction building flow.  Node runners can use the `transaction.submitSigned` method under `/account` in order to build, sign, and submit these transactions in a single step.  This also includes validator registration.

### New Configuration Options

The following configuration options control which APIs are enabled at the node:

| Configuration Option | Description |
| --- | --- |
| archive_api.enable | Enables `/archive` endpoint, disabled by default |
| construct_api.enable | Enables `/construct` endpoint, disabled by default |
| system_api.enable | Enables `/system` endpoint, **enabled** by default |
| account_api.enable | Enables `/account` endpoint, disabled by default |
| universe_api.enable | Enables `/universe.json` endpoint, disabled by default |
| faucet.enable | Enables `/faucet/request` endpoint for non-mainnet networks, disabled by default. On mainnet endpoint is always disabled |
| chaos.enable | Enables `/chaos/message-flooder` and `/chaos/mempool-filler ` endpoints for non-mainnet networks, disabled by default. On mainnet endpoints are always disabled |

Note that `/health` and `/version` REST endpoints are always enabled.  If a node runner wishes to disable access to them, they'll have to do so at a level in front of the node.
