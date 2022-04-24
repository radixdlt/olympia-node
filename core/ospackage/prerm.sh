#!/bin/sh

set -x

# kill the process
systemctl stop radixdlt.service || :