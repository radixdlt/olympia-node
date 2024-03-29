#!/bin/sh
set -e
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# docker-compose up works - but doesn't reattach to containers which are restarted
# So instead, we use the -d flag to start docker-compose in the background, and manually tail the logs

mkdir -p ./node/logs
touch ./node/logs/radixdlt-core.log
docker-compose up -d
tail -n0 -f ./node/logs/radixdlt-core.log