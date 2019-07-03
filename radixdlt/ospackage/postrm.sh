#!/bin/sh

set -ex

RADIXDLT_USER=radixdlt

# best effort: remove user & group
! getent passwd $RADIXDLT_USER >/dev/null || userdel $RADIXDLT_USER >/dev/null 2>&1 || :
! getent group $RADIXDLT_USER >/dev/null || groupdel $RADIXDLT_USER >/dev/null 2>&1 || :
