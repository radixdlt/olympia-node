### Current state of APIs as of May 10, 2021

### REST APIs

 Path | Method | Description | Comments 
 | --- | --- | --- | --- |
 /chaos/message-flooder | PUT | message flooder | Necessary for testnets only 
 /chaos/mempool-filler | PUT | mempool flooder |  Necessary for testnets only
 /faucet/request | POST | faucet API |  Necessary for testnets and betanet only 
 /node/parse | POST | Transaction blob parsing |  No sensitive information
 /node/txn | POST | Retrieve transaction blob |  No sensitive information
 /node/submit | POST | Submit transaction blob |  No sensitive information
 /node/execute | POST | Submit transaction using node private key to for signature | __Security sensitive__
 /node | GET | Get information about node - public address and balance | No sensitive information
 /node/validator | POST | Get information about node as a validator - stakes, registration status, etc.| No sensitive information
 /system/config | GET | Get active configuration parameters for consensus, mempool and RE | No sensitive information 
 /system/info | GET | Get system information - public key, agent, supported protocols | No sensitive information
 /system/checkpoints | GET | Get genesis txn and proof | No sensitive information
 /system/proof | GET | Get current proof | No sensitive information
 /system/epochproof | GET | Get epoch proof | No sensitive information
 /system/peers | GET | Get information about peer nodes | No sensitive information
 /universe.json | GET | Get Radix Universe | No sensitive information

### JSON-RPC APIs

Method | Description
| --- | --- |
|radix.nativeToken | Information about native token |
|radix.tokenInfo | Information about specified token |
|radix.tokenBalances | Token balances at specified account |
|radix.stakePositions | Stakes made from specified account |
|radix.unstakePositions | Pending unstakes for specified account|
|radix.transactionHistory | Transaction history for specified account |
|radix.lookupTransaction | Lookup specified transaction |
|radix.statusOfTransaction | Status of specified transaction |
|radix.buildTransaction | Assemble transaction given specified actions and optional message |
|radix.finalizeTransaction | Send signed transaction and receive transaction ID|
|radix.submitTransaction | Submit signed transaction for processing by network|
|radix.validators | Get list of validators |
|radix.lookupValidator | Get information about specified validator |
|radix.networkId | Get network ID |
|radix.networkTransactionThroughput | Get number of transactions per second |
|radix.networkTransactionDemand | Get average number of transactions waiting for processing in mempool |

### Configuration Options

Following configuration options control which APIs are enabled at the node:

Configuration Option | Description
| --- | --- |
|client_api.enable | Enables JSON-RPC APIs|
|universe_api.enable | Enables `/universe.json` API |
|faucet.enable | Enables `/faucet/request` API | 
|chaos.enable | Enables `/chaos/message-flooder` and `/chaos/mempool-filler ` APIs |

## New Organization of APIs

 The general approach to the API organization is based on the following considerations:
- All existing JSON RPC methods are grouped into two sets - Read-Only (R/O) methods and Read/Write (R/W) methods
- All methods are spread across three endpoints:
  - `/archive` (former `/rpc`) - client API, which can be configured to provide all or only R/O methods (see below).
  - `/system` supports new methods (see below) which provide same information as available today via all `/system/*` 
    and most `/node/*` endpoints
  - `/node` supports R/W methods from existing JSON RPC methods and one new method for one-step submission of 
    transactions signed with validator own private key (see below)
- The `/system` and `/node` endpoint is expected to be protected by firewall and/or require authentication/etc. 
  (same requirements/setup as we have today) 
- The `/system` endpoint is enabled by default but can be disabled with configuration options
- The `/node` endpoint is disabled by default but can be enabled with configuration options
- The `/archive` endpoint can be configured into one of the following states:
  - Disabled (default) - no methods are exposed and attempt to access `/archive` may return 404
  - R/O archive - `/archive` endpoint is enabled, but only R/O methods are supported
  - R/W archive - `/archive` endpoint is enabled and both, R/O and R/W methods are supported  

### REST APIs

Majority of the REST APIs are replaced with JSON-RPC counterparts. Remaining and new REST endpoints: 

Path | Method | Description
 | --- | --- | --- |
/health | GET | Returns node health status - `UP` or `SYNCYNG`. Standard endpoint for use by operations. Typical response: `{"status" : "UP" }`
/version | GET | Returns detailed information about software version and build info 
/faucet/request | POST | faucet API - betanet and local deployment only, disabled on mainnet
/universe.json | GET | Get Radix Universe. Used during setup and configuration of the node 

### JSON-RPC APIs

Method names are restructured into groups of related API methods. 
Since this is a breaking change, it is planned to support both method names until Beta 4.

As mentioned above, all three groups of methods listed below are available via `/system` JSON RPC endpoint.

Availability of the methods available via `/archive` endpoint depends on the configuration:
 - If client API is enabled, methods marked as R/O are available via `/archive` JSON RPC endpoint
 - If client API is enabled and __R/W__ methods are enabled, then `/archive` has same methods as `/system`.
 - If client API is disabled, then `/archive` endpoint is deactivated and may return HTTP status code 404.

#### Renamed methods:

Old Method Name | New Method Name | Access
| --- | --- | --- | 
| radix.nativeToken |token.native| R/O |
| radix.tokenInfo |token.info| R/O |
| radix.tokenBalances |address.balances| R/O |
| radix.stakePositions |address.stake.list| R/O |
| radix.unstakePositions |address.unstake.list| R/O |
| radix.transactionHistory |transaction.history| R/O |
| radix.lookupTransaction |transaction.lookup| R/O |
| radix.statusOfTransaction |transaction.status| R/O |
| radix.buildTransaction |transaction.build| __R/W__ |
| radix.finalizeTransaction |transaction.finalize| __R/W__ |
| radix.submitTransaction |transaction.submit| __R/W__ |
| radix.validators |validator.list| R/O |
| radix.lookupValidator |validator.lookup| R/O |
| radix.networkId | network.id| R/O |
| radix.networkTransactionThroughput |network.stat.throughput| R/O |
| radix.networkTransactionDemand |network.stat.demand| R/O |

#### Replacement methods for some old REST endpoints:

Methods below are accessible via `/ssystem` endpoint (if enabled)
   
Method | Description | Access
| --- | --- | --- |
| node.info | Complete information about node - public address, balance, validator registration status. If node is a validator, remaining details can be obtained from __validator.lookup__ | R/O |
| system.info | Complete information about system - consensus, mempool and RE configuration, public key, agent, protocols, genesis info, current and epoch proof | R/O |
| system.peers | Information about known peer nodes | R/O |

Methods below are accessible via `/node` endpoint (if enabled)

Method | Description | Access
| --- | --- | --- |
| transaction.build| The same as client API `transaction.build` method | __R/W__ |
| transaction.finalize| The same as client API `transaction.finalize` method |__R/W__ |
| transaction.submit| The same as client API `transaction.submit` method | __R/W__ |
| transaction.submitSigned| Equivalent to `transaction.build + transaction.finalize + transaction.submit` methods except does not require keys and signs transaction with node private key. Input parameters are same as for `transaction.build`, output is formatted as for `transaction.submit` method| __R/W__ |

#### New Actions 
In order to make JSON RPC API complete, we need to support following actions while building transactions: 

Action | Description 
| --- | --- |
| BurnTokens | Burn tokens |
| MintTokens | Mint tokens |
| RegisterValidator | Register node as validator |
| UnregisterValidator | Unregister node as validator |
| CreateFixedSupplyToken | Create fixed supply token |
| CreateMutableSupplyToken | Create mutable supply token |

### Proposed Configuration Options

Following configuration options control which APIs are enabled at the node:

Configuration Option | Description
| --- | --- |
|client_api.enable | Enables `/archive` endpoint. Which methods are available depends on the next option |
|client_api.rw | If `true` then both, R/O and R/W methods are available. If `false` then only R/O methods are enabled|
|system_api.enable | Enables `/system` endpoint. Enabled by default, to disable should be set to `false`|
|node_api.enable | Enables `/node` endpoint. Enabled by default, to disable should be set to `false`|
|universe_api.enable | Enables `/universe.json` endpoint|
|faucet.enable | Enables `/faucet/request` endpoint for non-mainnet networks. On mainnet endpoint is always disabled | 
|chaos.enable | Enables `/chaos/message-flooder` and `/chaos/mempool-filler ` endpoints for non-mainnet networks. On mainnet endpoints are always disabled |
