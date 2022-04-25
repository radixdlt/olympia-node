#!/bin/bash

# Fail on error
set -e

# Where we are run from
scriptdir=$(dirname "$0")

# Number of validators
validators=${1:-1}

# Check for dockerfile
dockerfile="${scriptdir}/../node-${validators}.yml"
if [ ! -f "${dockerfile}" ]; then
  echo "Can't find ${dockerfile}, aborting."
  exit 1
fi

reporoot="${scriptdir}/../.."

# Load environment
eval $(${reporoot}/gradlew -q -P "validators=${validators}" ":cli-tools:generateDevUniverse")

# Launch
${reporoot}/gradlew -p "${reporoot}" deb4docker && \
  (docker kill $(docker ps -q) || true) 2>/dev/null && \
  docker-compose -f "${dockerfile}" up --build | tee docker.log
