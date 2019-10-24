#!/bin/bash
scriptdir=$(dirname "$0")
dockerfile="${scriptdir}/../${1:-minimal-network}.yml"
if [ ! -f "${dockerfile}" ]; then
  echo "Can't find ${dockerfile}, aborting."
  exit 1
fi
${scriptdir}/../../gradlew clean -P obfuscation=off deb4docker && \
  (docker kill $(docker ps -q) || true) 2>/dev/null && \
  docker-compose -f "${dockerfile}" up --build | tee docker.log