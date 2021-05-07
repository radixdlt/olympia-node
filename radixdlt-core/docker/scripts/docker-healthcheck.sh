#!/bin/sh

set -e

# API is the last to come up when starting and node endpoint is always enabled on docker
exec curl http://$HOSTNAME:$RADIXDLT_NODE_API_PORT/node >/dev/null && netstat -ltn | grep -c 30000
