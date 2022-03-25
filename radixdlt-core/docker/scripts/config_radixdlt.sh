#!/bin/bash

set -ex

# need this to run on Alpine (grsec) kernels without crashing
find /usr -type f -name java -exec setfattr -n user.pax.flags -v em {} \;

#Add user using env variable if provided
USER_ID=${LOCAL_USER_ID:-999}
USER_NAME=radixdlt
# check and delete the user that is created in postinstal action of deb package
getent group $USER_NAME >/dev/null && groupmod -g $USER_ID radixdlt || groupadd -r $USER_NAME -g $USER_ID
getent passwd $USER_NAME >/dev/null && usermod -u $USER_ID radixdlt || useradd -r -d "$RADIXDLT_HOME" -g $USER_NAME $USER_NAME
chown -R radixdlt:radixdlt /home/radixdlt/
chmod u=xr /opt/radixdlt/bin/radixdlt

#check for test network configs
TEST_CONFIGS="${RADIXDLT_HOME:?}"/test.config
if test -f "$TEST_CONFIGS"; then
    echo >> default.config.envsubst
    cat $TEST_CONFIGS >> default.config.envsubst
fi

env | sort

envsubst <"${RADIXDLT_HOME:?}"/default.config.envsubst >"${RADIXDLT_HOME:?}"/default.config

exec setuidgid radixdlt $*
