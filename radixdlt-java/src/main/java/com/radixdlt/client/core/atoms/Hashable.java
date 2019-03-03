package com.radixdlt.client.core.atoms;

import org.radix.common.ID.EUID;

public interface Hashable {
	RadixHash hash();

	default EUID hid() {
		return this.hash().toEUID();
	}
}
