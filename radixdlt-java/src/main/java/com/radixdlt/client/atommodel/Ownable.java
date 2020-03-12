package com.radixdlt.client.atommodel;

import com.radixdlt.identifiers.RadixAddress;

/**
 * Temporary interface for representing a particle that can be owned and only invalidated by an owner.
 */
public interface Ownable {
	RadixAddress getAddress();
}
