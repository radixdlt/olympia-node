### General information about API's available at node

## Current state as of May 10, 2021

#### REST API's

 Path | Method | Description 
 | --- | --- | --- |
 /chaos/message-flooder | PUT | |
 /chaos/mempool-filler | PUT | |
 /faucet/request | POST | |
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
|radix.nativeToken ||
|radix.tokenInfo ||
|radix.tokenBalances ||
|radix.stakePositions ||
|radix.unstakePositions ||
|radix.transactionHistory ||
|radix.lookupTransaction ||
|radix.statusOfTransaction ||
|radix.buildTransaction ||
|radix.finalizeTransaction ||
|radix.submitTransaction ||
|radix.validators ||
|radix.lookupValidator ||
|radix.networkId | |
|radix.networkTransactionThroughput ||
|radix.networkTransactionDemand ||

## Proposed configuration

#### REST API's
Path | Method | Description | Example 
 | --- | --- | --- | --- |
/health | GET | Returns node health status - `UP` or `SYNCYNG` | `{"status" : "UP" }`
--/system/config | GET | |
/system/info | GET | |
/system/checkpoints | GET | |
/system/proof | GET | |
/system/epochproof | GET | |
/system/peers | GET | |
/universe.json | GET | |

#### JSON-RPC API's
Two major changes:
1. Existing JSON-RPC API's should be accessible via two names (old and new):

Old Method Name | New Method Name
| --- | --- | 
| radix.nativeToken |token.native|
| radix.tokenInfo | token.info|
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

2. Moved/converted REST API's and new methods

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

