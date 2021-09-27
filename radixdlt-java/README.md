# radixdlt-java

radixdlt-java is a Java Client library for interacting with a [Radix](https://www.radixdlt.com) Distributed Ledger.

## Table of contents
- [General Overview](#general-overview)
- [Links](#links)
- [License](#license)

## General Overview
The Java Client library consists of 3 [implementations](#implementations) but API structure is identical regardless from the 
implementation. 

## Client API structure
In order to keep API maintainable, it is split into following groups:

| Group | Description |
|---|---|
| __Network__| General information about network: ID, configuration, nodes, etc.
| __Transaction__| General purpose API for building and sending transactions, checking status, etc.
| __Token__| Information about tokens
| __Local__| Information about the node as well as single step transaction submission
| __SingleAccount__| Information related to single account: balances, transaction history, etc.
| __Validator__| List and lookup information about validators known to network
| __Api__| API configuration and metric counters
| __Consensus__| Consensus configuration and metric counters
| __Mempool__| Mempool configuration and metric counters
| __RadixEngine__| Radix Engine configuration and metric counters
| __Sync__| Node synchronization configuration and metric counters
| __Ledger__| Ledger proofs and checkpoints information

Note, that for various reasons Radix API spread across several endpoints split into two large groups
accessible at different ports. These results need to provide two ports while configuring the client. 

## Implementations
- [Asynchronous client with functional API](#asynchronous-client)
- [Synchronous client with functional API](#synchronous-client)
- [Synchronous client with imperative API](#imperative-client)

### Asynchronous Client
The class `com.radixdlt.client.lib.api.async.RadixApi` is a high-level Radix client with asynchronous functional API.
The API uses `Promise<T>` monad as a return value, which enables functional composition and transparent error handling
without involving `null` values and exceptions.

### Synchronous Client
The class `com.radixdlt.client.lib.api.sync.RadixApi` is a high-level Radix client with synchronous functional API.
Similar to the asynchronous API, synchronous API uses `Result<T>` monad as a return value, which enables functional composition and transparent error handling
without involving `null` values and exceptions.

### Imperative Client
The class `com.radixdlt.client.lib.api.sync.ImperativeRadixApi` is a high-level Radix client with
imperative API. It provides a wrapper around [synchronous client](#synchronous-client) with API structured 
in a more traditional style - it returns results of the execution directly and throws an exception in case of error.

## API Usage

Very brief introduction provided below should give a basic overview of the client library.

### Creating the client
In order to start using the client it should be created. In order to create client following information is necessary:
- URL, the base URL (without path) to the host where resides node we want to connect.
- Primary and secondary ports - two ports necessary to access different parts of the Radix Node API.
- (optional) login/password to access endpoints protected by the HTTP Basic authentication

Note: examples below may use parameters which not necessarily correspond to real ones.

Async client:
```java
import com.radixdlt.client.lib.api.async;
...
var client = RadixApi.connect("https://rcnet.radixdlt.com/system", 443, 443);
```

Sync client:
```java
import com.radixdlt.client.lib.api.sync;
...
var client = RadixApi.connect("https://rcnet.radixdlt.com/system", 443, 443);
```

Imperative client:
```java
import com.radixdlt.client.lib.api.sync;
...
var client = ImperativeRadixApi.connect("https://rcnet.radixdlt.com/system", 443, 443);
```

### Simple API Call

Sync/Async client:
```java
import com.radixdlt.client.lib.api.async;
...
    RadixApi.connect("https://rcnet.radixdlt.com/system", 443, 443)
        .onSuccess(client -> client.network().id()
                       .onSuccess(System.out::println));
```

Imperative client:
```java
import com.radixdlt.client.lib.api.sync;
...
    var client = ImperativeRadixApi.connect("https://rcnet.radixdlt.com/system", 443, 443);
    var networkId = client.network().id();
    System.out.println(networkId);
```

## Links

| Link | Description |
| ----- | ------ |
[radixdlt.com](https://radixdlt.com/) | Radix DLT Homepage
[documentation](https://docs.radixdlt.com/) | Radix Tech Docs
[@radixdlt](https://twitter.com/radixdlt) | Follow Radix DLT on Twitter

## License

The `radixdlt-java` code is released under the [Radix License](../LICENSE).