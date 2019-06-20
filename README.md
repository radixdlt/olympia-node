# radixdlt-java

[![](https://jitpack.io/v/com.radixdlt/radixdlt-java.svg)](https://jitpack.io/#com.radixdlt/radixdlt-java) [![Build Status](https://travis-ci.org/radixdlt/radixdlt-java.svg?branch=master)](https://travis-ci.org/radixdlt/radixdlt-java) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java) [![Reliability](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating) [![Security](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating) [![Code Corevage](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=coverage)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=Coverage)

radixdlt-java is a Java/Android Client library for interacting with a [Radix](https://www.radixdlt.com) Distributed Ledger.

## Features
* Connection to the Betanet test network 
* Fee-less transactions for testnets
* Public Key Identity Creation
* Native token transfers
* Fixed-supply/Mutable-supply Token Creation
* Immutable data storage
* RXJava 2 based
* Utilizes JSON-RPC over Websockets

# Getting Started
Include the following gradle dependency:
## Gradle
```
repositories {
    maven { url 'https://jitpack.io' }
}

```
```
dependencies {
    implementation 'com.radixdlt:radixdlt-java:0.11.6'
}
```

# Identities
An Identity is the user's credentials (or more technically the manager of the
public/private key pair) into the ledger, allowing a user to own tokens and send tokens
as well as decrypt data.

To create/load an identity from a file:
```
RadixIdentity identity = RadixIdentities.loadOrCreateEncryptedFile("filename.key", "password");
```
This will either create or load a file with a public/private key and encrypted with the given password.

# Universes
A Universe is an instance of a Radix Distributed Ledger which is defined by a genesis atom and
a dynamic set of unpermissioned nodes forming a network.

# Radix Application API
The Radix Application API is a client side API exposing high level abstractions to make
DAPP creation easier.

To initialize the API:
```
RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.BETANET, identity);
```

To continually sync and pull from the network ledger on your account:
```
Disposable d = api.pull();
```

To stop syncing:
```
d.dispose();
```

## Addresses
An address is a reference to an account and allows a user to receive tokens and/or data from other users.

You can get your own address by:
```
RadixAddress myAddress = api.getMyAddress();
```

Or from a base58 string:
```
RadixAddress anotherAddress = RadixAddress.fromString("JHB89drvftPj6zVCNjnaijURk8D8AMFw4mVja19aoBGmRXWchnJ");
```

## Storing and Retrieving Data
Immutable data can be stored on the ledger. The data can be encrypted so that only
selected identities can read the data.

To store the encrypted string `Hello` which only the user can read:
```
Result result = api.sendMessage("Hello".getBytes(StandardCharsets.UTF_8), true);
result.blockUntilComplete();
```

To store the unencrypted string `Hello`:
```
Result result = api.sendMessage("Hello".getBytes(StandardCharsets.UTF_8), false);
result.blockUntilComplete();
```

To then read (and decrypt if necessary) all the readable data at an address:
```
Observable<DecryptedMessage> readable = api.getMessages();
readable.subscribe(data -> { ... });
```

## Creating Tokens
To create a token, an RRI or radix resource identifier must first be constructed:
```
RRI tokenRRI = RRI.of(api.getMyAddress(), "NEW");
```

To create a fixed-supply token:
```
Result result = api.createFixedSupplyToken(tokenRRI, "New Token", "The Best Token", BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

To create a multi-issuance token:
```
Result result = api.createMultiIssuance(tokenRRI, "New Token", "The Best Token");
result.blockUntilComplete();
```

## Minting Tokens
To mint 1000 tokens (must be multi-issuance) in your account:
```
Result result = api.mintTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

## Burning Tokens
To burn 1000 tokens (must be multi-issuance) in your account:
```
Result result = api.burnTokens(tokenRRI, BigDecimal.valueOf(1000.0));
result.blockUntilComplete();
```

## Sending and Retrieving Tokens
To send an amount from my address to another address:
```
Result result = api.sendTokens(tokenRRI, BigDecimal.valueOf(10.99), <to-address>);
result.blockUntilComplete();
```

To retrieve all of the token transfers which have occurred in my account:
```
Observable<TokenTransfer> transfers = api.getTokenTransfers();
transfers.subscribe(tx -> { ... });
```

To get a stream of the balance of tokens in my account:
```
Observable<BigDecimal> balance = api.getBalance(tokenRRI);
balance.subscribe(bal -> { ... });
```
