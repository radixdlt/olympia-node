# radixdlt-java

[![Build Status](https://travis-ci.org/radixdlt/radixdlt-java.svg?branch=master)](https://travis-ci.org/radixdlt/radixdlt-java) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java) [![Reliability](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=reliability_rating) [![Security](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=security_rating) [![Code Corevage](https://sonarcloud.io/api/project_badges/measure?project=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=coverage)](https://sonarcloud.io/component_measures?id=com.radixdlt%3Aradixdlt-java%3Aradixdlt-java&metric=Coverage)

radixdlt-java is a Java/Android Client library for interacting with a [Radix](https://www.radixdlt.com) Distributed Ledger.

## Features
* Connection to the Alphanet test network 
* Fee-less transactions for the time being
* Identity Creation
* Native token transfers
* Immutable data storage
* Instant Messaging and TEST token wallet Dapp implementation
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
    implementation 'com.radixdlt:radixdlt-java:v0.10.0'
}
```

# Identities
An Identity is the user's credentials (or more technically the manager of the
public/private key pair) into the ledger, allowing a user to own tokens and send tokens
as well as decrypt data.

To create a new identity:
```
RadixIdentity identity = new EncryptedRadixIdentity("password", "filename.key");
```
This will create a new file which stores the public/private key and encrypted with the given password.

To read an identity file, simply run the same constructor with the correct password:
```
RadixIdentity identity = new EncryptedRadixIdentity("password", "filename.key");
```

# Universes
A Universe is an instance of a Radix Distributed Ledger which is defined by a genesis atom and
a dynamic set of unpermissioned nodes forming a network.

To bootstrap to the Alphanet test network:
```
RadixUniverse.bootstrap(Bootstrap.ALPHANET);
```
NOTE: No network connections will be made yet until it is required.

# Radix Dapp API
The Radix Application API is a client side API exposing high level abstractions to make
DAPP creation easier.

To initialize the API:
```
RadixUniverse.bootstrap(Bootstrap.ALPHANET); // This must be called before RadixApplicationAPI.create()
RadixApplicationAPI api = RadixApplicationAPI.create(identity);
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

To store the encrypted string `Hello` which only I can read into an account:
```
ECPublicKey myPublicKey = api.getMyPublicKey();
Data data = new DataBuilder().bytes("Hello".getBytes()).addReader(myPublicKey)
			.build();
Result result = api.storeData(data, <address>);
```

To store unencrypted data:
```
Data data = new DataBuilder().bytes("Hello World".getBytes()).unencrypted()
			.build();
Result result = api.storeData(data, <address>);
```

The returned `Result` object exposes RXJava interfaces from which you can get
notified of the status of the storage action:

```
result.toCompletable().subscribe(<on-success>, <on-error>);
```

To then read (and decrypt if necessary) all the readable data at an address:
```
Observable<UnencryptedData> readable = api.getReadableData(<address>);
readable.map(data -> { ... });
```

NOTE: data which is not decryptable by the user's key is simply ignored

## Sending and Retrieving Tokens
To send an amount of TEST (the testnet native token) from my account to another address:
```
Result result = api.sendTokens(<to-address>, Amount.of(10, Asset.TEST));
```

To retrieve all of the token transfers which have occurred in my account:
```
Observable<TokenTransfer> transfers = api.getMyTokenTransfers(Asset.TEST);
transfers.subscribe(tx -> { ... });
```

To get a stream of the balance of TEST tokens in my account:
```
Observable<Amount> balance = api.getMyBalance(Asset.TEST);
balance.subscribe(bal -> { ... });
```
