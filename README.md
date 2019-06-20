# radixdlt-java

[![](https://jitpack.io/v/com.radixdlt/radixdlt-java.svg)](https://jitpack.io/#com.radixdlt/radixdlt-java) [![Build Status](https://travis-ci.org/radixdlt/radixdlt-java.svg?branch=master)](https://travis-ci.org/radixdlt/radixdlt-java) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java) [![Reliability](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating) [![Security](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating) [![Code Corevage](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=coverage)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=Coverage)

radixdlt-java is a Java/Android Client library for interacting with a [Radix](https://www.radixdlt.com) Distributed Ledger.

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [Getting started](#getting-started)
- [Radix Application API](#radix-application-api)
- [Code examples](#code-examples)
- [Contribute](#contribute)
- [Links](#links)
- [License](#license)

## Features
* Connection to the Betanet test network 
* Fee-less transactions for testnets
* Public Key Identity Creation
* Native token transfers
* Fixed-supply/Mutable-supply Token Creation
* Immutable data storage
* RXJava 2 based
* Utilizes JSON-RPC over Websockets

## Installation
Include the following gradle dependency:
### Gradle
```
repositories {
    maven { url 'https://jitpack.io' }
}

```
```
dependencies {
    implementation 'com.radixdlt:radixdlt-java:0.12.0'
}
```

## Getting Started

### Identities
An Identity is the user's credentials (or more technically the manager of the
public/private key pair) into the ledger, allowing a user to own tokens and send tokens
as well as decrypt data.

To create/load an identity from a file:
```java
RadixIdentity identity = RadixIdentities.loadOrCreateEncryptedFile("filename.key", "password");
```
This will either create or load a file with a public/private key and encrypted with the given password.

### Universes
A Universe is an instance of a Radix Distributed Ledger which is defined by a genesis atom and
a dynamic set of unpermissioned nodes forming a network.

A predefined configuration to bootstrap into the betanet network is available:
```java
BootstrapConfig config = Bootstrap.BETANET;
```

## Radix Application API
The Radix Application API is a client side API exposing high level abstractions to make
DAPP creation easier.

To initialize the API:
```java
RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.BETANET, identity);
```

To continually sync and pull from the network ledger on your account:
```java
Disposable d = api.pull();
```

To stop syncing:
```java
d.dispose();
```

### Addresses
An address is a reference to an account and allows a user to receive tokens and/or data from other users.

You can get your own address by:
```java
RadixAddress myAddress = api.getMyAddress();
```

Or from a base58 string:
```java
RadixAddress anotherAddress = RadixAddress.fromString("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
```

## Code Examples

### Storing and Retrieving Data
Immutable data can be stored on the ledger. The data can be encrypted so that only
selected identities can read the data.

To store the encrypted string `Hello` which only the user can read:
```java
Result result = api.sendMessage("Hello".getBytes(StandardCharsets.UTF_8), true);
result.blockUntilComplete();
```

To store the unencrypted string `Hello`:
```java
Result result = api.sendMessage("Hello".getBytes(StandardCharsets.UTF_8), false);
result.blockUntilComplete();
```

To then read (and decrypt if necessary) all the readable data at an address:
```java
Observable<DecryptedMessage> readable = api.getMessages();
readable.subscribe(data -> { ... });
```

### Creating Tokens
To create a token, an RRI or radix resource identifier must first be constructed:
```java
RRI tokenRRI = RRI.of(api.getMyAddress(), "NEW");
```

To create a fixed-supply token:
```java
Result result = api.createFixedSupplyToken(tokenRRI, "New Token", "The Best Token", BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

To create a multi-issuance token:
```java
Result result = api.createMultiIssuance(tokenRRI, "New Token", "The Best Token");
result.blockUntilComplete();
```

### Minting Tokens
To mint 1000 tokens (must be multi-issuance) in your account:
```java
Result result = api.mintTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

### Burning Tokens
To burn 1000 tokens (must be multi-issuance) in your account:
```java
Result result = api.burnTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

### Sending and Retrieving Tokens
To send an amount from my address to another address:
```java
Result result = api.sendTokens(tokenRRI, BigDecimal.valueOf(10.99), <to-address>);
result.blockUntilComplete();
```

To retrieve all of the token transfers which have occurred in my account:
```java
Observable<TokenTransfer> transfers = api.getTokenTransfers();
transfers.subscribe(tx -> { ... });
```

To get a stream of the balance of tokens in my account:
```java
Observable<BigDecimal> balance = api.getBalance(tokenRRI);
balance.subscribe(bal -> { ... });
```


## Contribute

Contributions are welcome, we simply ask to:

* Fork the codebase
* Make changes
* Submit a pull request for review

When contributing to this repository, we recommend to discuss the change you wish to make via issue,
email, or any other method with the owners of this repository before making a change. 

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all your interactions with the project.

## Links

| Link | Description |
| :----- | :------ |
[radixdlt.com](https://radixdlt.com/) | Radix DLT Homepage
[documentation](https://docs.radixdlt.com/) | Radix Knowledge Base
[forum](https://forum.radixdlt.com/) | Radix Technical Forum
[@radixdlt](https://twitter.com/radixdlt) | Follow Radix DLT on Twitter

## License

radixdlt-java is released under the [MIT License](LICENSE).
