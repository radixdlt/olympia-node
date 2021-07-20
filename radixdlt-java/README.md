# radixdlt-java

[![](https://jitpack.io/v/com.radixdlt/radixdlt-java.svg)](https://jitpack.io/#com.radixdlt/radixdlt-java) [![Build Status](https://api.travis-ci.com/radixdlt/radixdlt-java.svg?branch=master)](https://travis-ci.org/radixdlt/radixdlt-java) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.radixdlt%3Aradixdlt-java) [![Reliability](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java&metric=reliability_rating) [![Security](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java&metric=security_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java&metric=security_rating) [![Code Corevage](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java&metric=coverage)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java&metric=Coverage)

radixdlt-java is a Java/Android Client library for interacting with a [Radix](https://www.radixdlt.com) Distributed Ledger.

## Table of contents

- [Changelog](CHANGELOG.md)
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
* Token Creation (ERC-777 style)
* Message sending
* RXJava 2 based
* Utilizes JSON-RPC over Websockets

## Getting Started

### Identities
An Identity is the user's credentials (or more technically the manager of the
public/private key pair) into the ledger, allowing a user to own tokens and send tokens
as well as decrypt data.

To create/load an identity from a file:
```java
RadixIdentity identity = RadixIdentities.loadOrCreateEncryptedFile("filename.key", "password123", "key_name");
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
RadixAddress myAddress = api.getAddress();
```

Or from a base58 string:
```java
RadixAddress anotherAddress = RadixAddress.fromString("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
```

## Code Examples

### Sending Messages
Immutable data can be stored on the ledger. The data can be encrypted so that only
selected identities can read the data.

To send the encrypted string `Hello` which only the sender and recipient can read:
```java
Result result = api.sendMessage(<to-address>, "Hello".getBytes(StandardCharsets.UTF_8), true);
result.blockUntilComplete();
```

To send the unencrypted string `Hello`:
```java
Result result = api.sendMessage(<to-address>, "Hello".getBytes(StandardCharsets.UTF_8), false);
result.blockUntilComplete();
```

Or equivalently,
```java
SendMessageAction msgAction = SendMessageAction.create(api.getAddress(), <to-address>, "Hello".getBytes(StandardCharset.UTF_8), false);
Result result = api.execute(msgAction);
result.blockUntilComplete();
```

### Receiving Messages

To then read (and decrypt if necessary) all the readable data sent to you:
```java
Observable<DecryptedMessage> readable = api.observeMessages();
readable.subscribe(data -> { ... });
```

### Creating Tokens
To create a token, an RRI or radix resource identifier must first be constructed:
```java
RRI tokenRRI = RRI.of(api.getAddress(), "NEW");
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

Or equivalently,
```java
CreateTokenAction createAction = CreateTokenAction.create(
  tokenRRI,
  "New Token",
  "The Best Token",
  BigDecimal.ZERO,
  TokenUnitConversions.getMinimumGranularity(),
  TokenSupplyType.MUTABLE
); 
Result result = api.execute(createAction);
result.blockUntilComplete();
```

### Minting Tokens
To mint 1000 tokens (must be multi-issuance) in your account:
```java
Result result = api.mintTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

Or equivalently,
```java
MintTokensAction mintAction = MintTokensAction.create(tokenRRI, api.getAddress(), BigDecimal.valueOf(1000.0));
Result result = api.execute(mintAction);
result.blockUntilComplete();
```

### Burning Tokens
To burn 1000 tokens (must be multi-issuance) in your account:
```java
Result result = api.burnTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

Or equivalently,
```java
BurnTokensAction burnAction = BurnTokensAction.create(tokenRRI, api.getAddress(), BigDecimal.valueOf(1000.0));
Result result = api.execute(burnAction);
result.blockUntilComplete();
```

### Sending Tokens
To send an amount from my address to another address:
```java
Result result = api.sendTokens(tokenRRI, BigDecimal.valueOf(10.99), <to-address>);
result.blockUntilComplete();
```

Or equivalently,
```java
TransferTokensAction sendAction = TransferTokensAction.create(
  tokenRRI,
  api.getAddress(),
  <to-address>,
  BigDecimal.valueOf(10.00),
  null
);
Result result = api.execute(sendAction);
result.blockUntilComplete();
```

### Retrieving Tokens
To retrieve all of the token transfers which have occurred in my account:
```java
Observable<TokenTransfer> transfers = api.observeTokenTransfers();
transfers.subscribe(tx -> { ... });
```

To get a stream of the balance of tokens in my account:
```java
Observable<BigDecimal> balance = api.observeBalance(tokenRRI);
balance.subscribe(bal -> { ... });
```

### Executing Atomic Transactions
To execute an atomic transaction of creating a token, minting, then sending:
```java
CreateTokensAction createAction = CreateTokenAction.create(
  tokenRRI,
  "Joshy Token",
  "The Best Coin Ever",
  BigDecimal.ZERO,
  TokenUnitConversions.getMinimumGranularity(),
  TokenSupplyType.MUTABLE
);
MintTokensAction mintAction = MintTokensAction.create(
  tokenRRI,
  api.getAddress(),
  BigDecimal.valueOf(1000000.0)
);
TransferTokensAction transferAction =  TransferTokensAction.create(
  tokenRRI,
  api.getAddress(),
  <to-address>,
  BigDecimal.valueOf(1000000.0),
  null
);

Transaction tx = api.createTransaction();
tx.stage(createAction);
tx.stage(mintAction);
tx.stage(transferAction);
Result result = tx.commitAndPush();
result.blockUntilComplete();

```

## Contribute

[Contributions](../CONTRIBUTING.md) are welcome, we simply ask to:

* Fork the codebase
* Make changes
* Submit a pull request for review

When contributing to this repository, we recommend discussing with the development team the change you wish to make using a [GitHub issue](https://github.com/radixdlt/radixdlt/issues) before making changes.

Please follow our [Code of Conduct](../CODE_OF_CONDUCT.md) in all your interactions with the project.

## Links

| Link | Description |
| :----- | :------ |
[radixdlt.com](https://radixdlt.com/) | Radix DLT Homepage
[documentation](https://docs.radixdlt.com/) | Radix Knowledge Base
[forum](https://forum.radixdlt.com/) | Radix Technical Forum
[@radixdlt](https://twitter.com/radixdlt) | Follow Radix DLT on Twitter
