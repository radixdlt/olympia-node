package com.radixdlt.client.atommodel;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * Temporary interface for representing a particle that can be owned and only invalidated by an owner.
 */
public interface Ownable {
	ECPublicKey getOwner();
	RadixAddress getAddress();
}
