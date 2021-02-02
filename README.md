# radixdlt

This is the monorepo for consensus, networking and Java client library for the [Radix DLT](https://www.radixdlt.com)
Distributed Ledger.

It includes a variant implementation of the [HotStuff](https://arxiv.org/abs/1803.05069) BFT-style consensus.

## Table of contents

- [Subdirectories](#subdirectories)
- [Contribute](#contribute)
- [Links](#links)
- [License](#license)

## Subdirectories
Here we have:

- [radixdlt-core](radixdlt-core/README.md): The core consensus and networking modules
- [radixdlt-engine](radixdlt-engine/README.md): The Radix execution environment which provides an engine for a real-time, distributed state machine
- [radixdlt-java-common](radixdlt-java-common/README.md): Common Java utilities used by various modules
- [radixdlt-java](radixdlt-java/README.md): The Java client access library
- [radixdlt-regression](radixdlt-regression/README.md): Regression tests

## Contribute

[Contributions](CONTRIBUTING.md) are welcome, we simply ask to:

* Fork the codebase
* Make changes
* Submit a pull request for review

When contributing to this repository, we recommend discussing with the development team the change you wish to make using a [GitHub issue](https://github.com/radixdlt/radixdlt/issues) before making changes.

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md) in all your interactions with the project.

## Links

| Link | Description |
| :----- | :------ |
[radixdlt.com](https://radixdlt.com/) | Radix DLT Homepage
[documentation](https://docs.radixdlt.com/) | Radix Knowledge Base
[forum](https://forum.radixdlt.com/) | Radix Technical Forum
[@radixdlt](https://twitter.com/radixdlt) | Follow Radix DLT on Twitter

## License

The `radixdlt-core`, `radixdlt-engine`, `radixdlt-java-common`, `radixdlt-regression` code is released under the [Apache 2.0 License](LICENSE).

The `radixdlt-java` library is released under the [MIT License](radixdlt-java/LICENSE).
