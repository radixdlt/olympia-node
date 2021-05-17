# API Redesign Proposal

This document describes reorganization and proposed changes to the whole node API.

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
| chaos.enable | Enables `/chaos/message-flooder` and `/chaos/mempool-filler` APIs |

</details>

## New Organization of APIs

 The general approach to the API organization is based on the following considerations:
- All methods are spread across four top-level routes:
  - `/archive` (former `/rpc`) - read-only methods, requiring an archive node
  - `/construct` - methods which support building and submitting transactions, which can be enabled by either archive or full nodes
  - `/account` supports methods to fetch your associated account info, and a one-step method to build, sign, and submit a transaction
  - `/system` - read-only methods which provide same information as available today via all `/system/*` endpoints
  - `/validator` - read-only methods which provide same information as available today via all `/node/*` endpoints
    
- The `/system`, `/account` and `/validator` endpoints are expected to be protected by firewall and/or require authentication/etc. 
  (similar requirements/setup as we have today)
  
- The following endpoints are supported until mainnet release (necessary for testing/debugging):
  - `/chaos/message-flooder` 
  - `/chaos/mempool-filler`
  - `/faucet/request` 

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

#### /account

| Method | Description |
| --- | --- |
| account.info | Your account's address and balances (not node ID) |
| transaction.buildSignSubmit | Equivalent to `transaction.build + transaction.finalize + transaction.submit` methods except does not require keys and signs transaction with node private key. Input parameters are same as for `transaction.build`, output is formatted as for `transaction.submit` method |

#### /system

| Method | Description |
| --- | --- |
| system.config | Get active configuration parameters for consensus, mempool and RE |
| system.info | Get system information - public key, agent, supported protocols |
| system.checkpoints | Get genesis txn and proof |
| system.proof | Get current proof |
| system.epochproof | Get epoch proof |
| system.peers | Information about known peer nodes |

#### /validator

| Method | Description |
| --- | --- |
| validator.details | Get information about node as a validator - stakes, registration status, etc.

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
Note that all the wallet-style actions which were previously done via REST 
under `/node` are now supported via Actions under the normal transaction 
building flow.  Node runners can use the `transaction.buildSignSubmit` method 
under `/account` in order to build, sign, and submit these transactions in a 
single step.  This also includes validator registration.

### New Configuration Options

The following configuration options control which APIs are enabled at the node:

| Configuration Option | Path | Default if not configured
| --- | --- | --- |
| api.archive.enable | `/archive` | Disabled
| api.construct.enable | `/construct` | Disabled
| api.system.enable | `/system` | Disabled
| api.account.enable | `/account` | Disabled
| api.validator.enable | `/validator` endpoint | Disabled
| api.universe.enable | `/universe.json` | Disabled
| api.faucet.enable | `/faucet/request` | Disabled; __Can't be enabled on mainnet__
| api.chaos.enable | `/chaos/message-flooder` and `/chaos/mempool-filler`| Disabled; __Can't be enabled on mainnet__
| api.health.enable | `/health` | Enabled
| api.version.enable | `/version` | Enabled
