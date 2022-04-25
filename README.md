# Radix Babylon Node - Java

This is the monorepo for consensus, execution, and networking for the [Radix DLT](https://www.radixdlt.com)
Distributed Ledger.

It includes a variant implementation of the [HotStuff](https://arxiv.org/abs/1803.05069) BFT-style consensus.

## Subdirectories

Here we have:

- [core](core/README.md): The core consensus and networking modules
- [engine](engine/README.md): The Olympia Radix execution layer which provides a UTXO-based state machine
- [common](common/README.md): Common Java utilities used by various modules
- [cli-tools](cli-tools): Various basic command line helpers to assist with spinning up nodes and networks
- [shell](shell): The Radix shell, which can enable you to easily spin up nodes and interact with them on-the-fly

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

The code in this repository is released under the [Radix License](LICENSE).
