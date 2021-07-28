# radixdlt

This is the monorepo for consensus, execution, and networking for the [Radix DLT](https://www.radixdlt.com)
Distributed Ledger.

It includes a variant implementation of the [HotStuff](https://arxiv.org/abs/1803.05069) BFT-style consensus.

## Subdirectories

Here we have:

- [radixdlt-core](radixdlt-core/README.md): The core consensus and networking modules
- [radixdlt-engine](radixdlt-engine/README.md): The Radix execution layer which provides
  a UTXO-based state machine
- [radixdlt-java-common](radixdlt-java-common/README.md): Common Java utilities used by various modules

## Contribute

To contribute, you'll need to [setup development environment](docs/development/README.md).

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
[docs.radixdlt.com](https://docs.radixdlt.com/) | Radix Technical Documentation
[learn.radixdlt.com](https://learn.radixdlt.com/) | Radix Knowledge Base
[discord invite](https://discord.com/invite/WkB2USt) | Radix Discord Server

## License

The `radixdlt-core`, `radixdlt-engine`, `radixdlt-java-common` code is released under the [Radix License](LICENSE).
