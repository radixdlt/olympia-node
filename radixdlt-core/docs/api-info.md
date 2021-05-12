### General information about API's available at node

## Current state as of May 10, 2021

#### REST API's

 Path | Method | Description 
 | --- | --- | --- |
 /chaos/message-flooder | PUT | message flooder - testnets |
 /chaos/mempool-filler | PUT | mempool flooder - testnets |
 /faucet/request | POST | faucet API - betanet |
 /node/parse | POST | |
 /node/txn | POST | |
 /node/submit | POST | |
 /node/execute | POST | |
 /node | GET | |
 /node/validator | POST | |
 /system/config | GET | |
 /system/info | GET | |
 /system/checkpoints | GET | |
 /system/proof | GET | |
 /system/epochproof | GET | |
 /system/peers | GET | |
 /universe.json | GET | |

#### JSON-RPC API's

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

## Proposed configuration

#### REST API's
Path | Method | Description | Example 
 | --- | --- | --- | --- |
/health | GET | Returns node health status - `UP` or `SYNCYNG` | `{"status" : "UP" }`
/version | GET | Returns node software version and build info | `{ "version" : "1.0.33-e137738baa2d306efa4e1bdc637c62f16dc7a3fe" }` 
/faucet/request | POST | faucet API - betanet and local deployment only| 
/node/parse | POST | |
/node/txn | POST | |
/node/submit | POST | |
/node/execute | POST | |
/node | GET | |
/node/validator | POST | |
/system/config | GET | |
/system/info | GET | |
/system/checkpoints | GET | |
/system/proof | GET | |
/system/epochproof | GET | |
/system/peers | GET | |

#### JSON-RPC API's

Method names are restructured into groups of related API methods. Since this is a breaking change,
it is planned to support both method names until Beta 4.

1. Renamed methods:

Old Method Name | New Method Name
| --- | --- | 
| radix.nativeToken |token.native|
| radix.tokenInfo |token.info|
| radix.tokenBalances |address.balances|
| radix.stakePositions |address.stakes|
| radix.unstakePositions |address.unstakes|
| radix.transactionHistory |transaction.history|
| radix.lookupTransaction |transaction.lookup|
| radix.statusOfTransaction |transaction.status|
| radix.buildTransaction |transaction.build|
| radix.finalizeTransaction |transaction.finalize|
| radix.submitTransaction |transaction.submit|
| radix.validators |validator.list|
| radix.lookupValidator |validator.lookup|
| radix.networkId | network.id|
| radix.networkTransactionThroughput |network.stat.throughput|
| radix.networkTransactionDemand |network.stat.demand|

2. Method added as a replacement for some API's which 
   were implemented as REST API's:
   
Method | Description
| --- | --- |
| system.config | |
| system.info | |
| system.checkpoints | |
| system.proof | |
| system.epochproof | |
| system.peers | |
| system.universe | |
| node.balance | |
| node.validator | |
| node.universe | |

3. New methods:
   Method | Description
   | --- | --- |
   | token.issue | Issue new tokens |
   | validator.register | Register validator |
   | validator.unregister | Unregister validator |
   