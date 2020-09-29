#!/bin/bash

# Fail on error
set -e

# Where we are run from
scriptdir=$(dirname "$0")

# Check for dockerfile
dockerfile="${scriptdir}/../${1:-minimal-network}.yml"
if [ ! -f "${dockerfile}" ]; then
  echo "Can't find ${dockerfile}, aborting."
  exit 1
fi

# Load environment
eval $(${scriptdir}/../../gradlew -q -p "${scriptdir}/../../radixdlt" clean generateDevUniverse)

# Launch
${scriptdir}/../../gradlew -p "${scriptdir}/../.." deb4docker && \
  (docker kill $(docker ps -q) || true) 2>/dev/null && \
  docker-compose -f "${dockerfile}" up --build | tee docker.log
