# radixdlt-core

radixdlt-core is Radix' core consensus and networking module for the [Radix](https://www.radixdlt.com) Distributed Ledger.

## Table of contents

- [Building](#building)
- [Launching](#launching)
- [Contribute](#contribute)
- [Links](#links)
- [License](#license)

## Building
Clone the required repositories:
```
git clone https://github.com/radixdlt/radixdlt-core.git
git clone https://github.com/radixdlt/radix-engine-library.git
```

Checkout the required branches:
```
cd radix-engine-library && git checkout release/1.0-beta.4 && cd ..
cd radixdlt-core && git checkout release/1.0-beta.5
```

Run tests (note that integration tests take several minutes to run):
```
./gradlew test integrationTest
```
Unfortunately the integration tests currently use hardcoded TCP/UDP port
numbers 12345 and 23456, and may fail if these ports are not available on
your machine.  We plan to fix this.

## Launching
You will need docker installed to launch a node.  We use
[Docker Desktop for Mac](https://hub.docker.com/editions/community/docker-ce-desktop-mac).
```
./gradlew deb4docker
docker-compose -f docker/single-node.yml up --build
```
API interface can be accessed via localhost:8080, eg:
```
curl localhost:8080/api/universe
```
See also [radixdlt-java](https://github.com/radixdlt/radixdlt-java)
for a Java client library and [radixdlt-js](https://github.com/radixdlt/radixdlt-js)
for Javascript.

## Contribute

[Contributions](CONTRIBUTING.md) are welcome, we simply ask to:

* Fork the codebase
* Make changes
* Submit a pull request for review

When contributing to this repository, we recommend discussing with the development team the change you wish to make using a [GitHub issue](https://github.com/radixdlt/radixdlt-core/issues) before making changes.

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all your interactions with the project.

## Links

| Link | Description |
| :----- | :------ |
[radixdlt.com](https://radixdlt.com/) | Radix DLT Homepage
[documentation](https://docs.radixdlt.com/) | Radix Knowledge Base
[forum](https://forum.radixdlt.com/) | Radix Technical Forum
[@radixdlt](https://twitter.com/radixdlt) | Follow Radix DLT on Twitter

## License

The `radixdlt-core` code is released under the [Apache 2.0 License](LICENSE).
