package com.radixdlt.tempo;

import com.radixdlt.common.EUID;

// TODO remove and replace with regular AddressBook once it is integrated
public interface LegacyAddressBook {
	boolean contains(EUID nid);

	void addListener(LegacyAddressBookListener listener);

	boolean removeListener(LegacyAddressBookListener listener);
}
