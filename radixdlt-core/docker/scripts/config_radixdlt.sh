#!/bin/sh

set -ex

# need this to run on Alpine (grsec) kernels without crashing
find /usr -type f -name java -exec setfattr -n user.pax.flags -v em {} \;

#Add user using env variable if provided
USER_ID=${LOCAL_USER_ID:-999}
USER_NAME=radixdlt
# check and delete the user that is created in postinstal action of deb package
getent group $USER_NAME >/dev/null && groupmod -g $USER_ID radixdlt || groupadd -r $USER_NAME
getent passwd $RADIXDLT_USER >/dev/null && usermod -u $USER_ID radixdlt || useradd -r -d "$RADIXDLT_HOME" -g $RADIXDLT_USER $RADIXDLT_USER
chown -R radixdlt:radixdlt /home/radixdlt/

env | sort

envsubst <"${RADIXDLT_HOME:?}"/default.config.envsubst >"${RADIXDLT_HOME:?}"/default.config

exec su --preserve-environment -c "$*" radixdlt
