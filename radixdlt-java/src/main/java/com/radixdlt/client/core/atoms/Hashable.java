package com.radixdlt.client.core.atoms;

import org.radix.common.ID.EUID;

/**
 * Representing any type that can be *hashed* into a {@link RadixHash}
 * with a utility method to obtain an HID from that hash.
 */
public interface Hashable {
	/**
	 * Get the {@link RadixHash} of this object
	 * @return The hash of this object
	 */
	RadixHash hash();

	/**
	 * Get the {@link RadixHash} shortened into a EUID
	 * @return The HID
	 */
	default EUID hid() {
		return this.hash().toEUID();
	}
}
