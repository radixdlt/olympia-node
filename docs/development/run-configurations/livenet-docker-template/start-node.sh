#!/bin/sh

set -e

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

source ./node-variables.sh

docker-compose -f radix-develop-compose.yml up -d