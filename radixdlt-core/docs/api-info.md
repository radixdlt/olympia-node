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
- Most methods are spread across five endpoints:
  - `/archive` (formerly `/rpc`) - read-only methods, requiring an archive node
  - `/construction` - methods which support building and submitting transactions, which can be enabled by either archive or full nodes
  - `/account` supports methods to fetch your associated account info, and a one-step method to build, sign, and submit a transaction
  - `/validation` - read-only methods which provide same information as available today via all `/node/*` endpoints
  - `/system` - read-only methods which provide same information as available today via all `/system/*` endpoints
    
- The `/system`, `/account` and `/validator` endpoints are expected to be protected by firewall and/or require authentication/etc. 
  (similar requirements/setup as we have today)
  
- The following endpoints are supported until mainnet release (necessary for testing/debugging):
  - `/chaos/message-flooder` 
  - `/chaos/mempool-filler`
  - `/faucet` 

### REST APIs

Majority of the REST APIs are replaced with JSON-RPC counterparts. Remaining and new REST endpoints:

| Path | Method | Description |
| --- | --- | --- |
| /health | GET | Returns node health status - `UP` or `SYNCING`. Standard endpoint for use by operations. Typical response: `{"status" : "UP" }` |
| /version | GET | Returns detailed information about software version and build info |
| /universe.json | GET | Get Radix Universe. Used during setup and configuration of the node but if possible looking to remove |

### JSON-RPC APIs

#### /archive
| Method Name | Old Method Name |
| --- | --- |
| tokens.get_native_token | radix.nativeToken |
| tokens.get_info | radix.tokenInfo |
| account.get_balances | radix.tokenBalances |
| account.get_stake_positions | radix.stakePositions |
| account.get_unstake_positions | radix.unstakePositions |
| account.get_transaction_history | radix.transactionHistory |
| transactions.lookup_transaction | radix.lookupTransaction |
| transactions.get_transaction_status | radix.statusOfTransaction |
| validators.get_next_epoch_set | radix.validators |
| validators.lookup_validator | radix.lookupValidator |
| network.get_id | radix.networkId |
| network.get_throughput | radix.networkTransactionThroughput |
| network.get_demand | radix.networkTransactionDemand |

#### /construction
| Method Name | Old Method Name | Notes |
| --- | --- | --- |
| construction.build_transaction | radix.buildTransaction | Same as before |
| construction.finalize_transaction | radix.finalizeTransaction | Now returns a fully ready-to-submit blob and a transaction ID |
| construction.submit_transaction | radix.submitTransaction | Now accepts a single parameter, a ready-to-submit blob |

#### /account

| Method | Description |
| --- | --- |
| account.get_info | Your account's address and balances (not node ID) |
| account.submit_transaction_single_step | Equivalent to `transaction.build + transaction.finalize + transaction.submit` methods except does not require keys and signs transaction with node private key. Input parameters are same as for `transaction.build`, output is formatted as for `transaction.submit` method |

#### /system

| Method | Description |
| --- | --- |
| api.get_configuration | Get active configuration parameters for api |
| api.get_data | Get data for api |
| bft.get_configuration | Get active configuration parameters for consensus |
| bft.get_data | Get data for consensus |
| mempool.get_configuration | Get active configuration parameters for mempool |
| mempool.get_data | Get data for mempool |
| ledger.get_latest_proof | Get the latest known ledger proof |
| ledger.get_latest_epoch_proof | Get the latest known ledger epoch proof |
| radix_engine.get_configuration | Get active configuration parameters for radix engine |
| radix_engine.get_data | Get data for radix engine |
| sync.get_configuration | Get active configuration parameters for sync |
| sync.get_data | Get data for sync |
| networking.get_configuration | Get active configuration parameters for networking |
| networking.get_peers | Get information about known peer nodes |
| networking.get_data | Get data for networking |
| checkpoints.get_checkpoints | Get genesis txn and proof |

#### /validation

| Method | Description |
| --- | --- |
| validation.get_node_info | Get information about node as a validator - stakes, registration status, etc.
| validation.get_current_epoch_data | Get information about the current set of validators
| validation.get_next_epoch_data | Get information about the next set of validators


#### /faucet

| Method | Description |
| --- | --- |
| faucet.request_tokens | Make a request for tokens for a particular account

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
building flow.  Node runners can use the `account.submit_transaction_single_step` method 
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
| api.validation.enable | `/validation` endpoint | Disabled
| api.universe.enable | `/universe.json` | Disabled
| api.faucet.enable | `/faucet/request` | Disabled; __Can't be enabled on mainnet__
| api.chaos.enable | `/chaos/message-flooder` and `/chaos/mempool-filler`| Disabled; __Can't be enabled on mainnet__
| api.health.enable | `/health` | Enabled
| api.version.enable | `/version` | Enabled
