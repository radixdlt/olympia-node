#!/bin/sh

set -ex

# need this to run on Alpine (grsec) kernels without crashing
find /usr -type f -name java -exec setfattr -n user.pax.flags -v em {} \;

ulimit -n 65536
env | sort

envsubst <"${RADIXDLT_HOME:?}"/default.config.envsubst >"${RADIXDLT_HOME:?}"/default.config

exec su --preserve-environment -c "$*" radixdlt
