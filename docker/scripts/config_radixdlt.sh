#!/bin/sh

set -ex

# need this to run on Alpine (grsec) kernels without crashing
find /usr -type f -name java -exec setfattr -n user.pax.flags -v em {} \;

# Loosing entropy requirements to avoid blocking based on the the /dev/urandom manpage: https://linux.die.net/man/4/urandom
# ... "as a general rule, /dev/urandom should be used for everything except long-lived GPG/SSL/SSH keys" ...
find / -type f -name java.security | xargs sed -i "s#^\s*securerandom.source=file:.*#securerandom.source=file:/dev/urandom#g"

env | sort

envsubst <"${RADIXDLT_HOME:?}"/default.config.envsubst >"${RADIXDLT_HOME:?}"/default.config

exec su --preserve-environment -c "$*" radixdlt
