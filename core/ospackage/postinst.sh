#!/bin/sh

set -ex

# vars
RADIXDLT_USER=radixdlt
RADIXDLT_HOME=/opt/$RADIXDLT_USER

# create user and group idempotently
getent group $RADIXDLT_USER >/dev/null || groupadd -r $RADIXDLT_USER
getent passwd $RADIXDLT_USER >/dev/null || useradd -r -d "$RADIXDLT_HOME" -g $RADIXDLT_USER $RADIXDLT_USER

# create log dir
mkdir -p /var/log/$RADIXDLT_USER

# make sure all files are owned by the radixdlt user/group
chown -Rf "$RADIXDLT_USER:$RADIXDLT_USER" "$RADIXDLT_HOME" /var/log/$RADIXDLT_USER

# Make sure that systemd files are owned by root
chown root:root /etc/systemd/system/$RADIXDLT_USER.service

#systemctl daemon-reload
#systemctl start $RADIXDLT_USER.service
