# Connecting a development build to a live network with Docker

This set-up is useful to assess the current build's compatibility against existing
networks or ledgers, or to pull through a more interesting ledger record.

The node will be run from a folder not under this repository. That folder captures the run configuration and
ledger for that node.

## Getting set-up

* Copy the [livenet-docker-template](./livenet-docker-template) folder to a new folder of your choosing
 outside this repository, and give that folder a sensible name, eg `radix-stokenet-node` for a stokenet node.
* Follow the instructions in the [README in that folder](./livenet-docker-template).