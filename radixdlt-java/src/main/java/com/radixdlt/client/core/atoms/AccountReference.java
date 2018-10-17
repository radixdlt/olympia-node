package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

public class AccountReference {
	private final ECKeyPair key;

	public AccountReference(ECPublicKey key) {
		this.key = key.toECKeyPair();
	}

	public ECPublicKey getKey() {
		return key.getPublicKey();
	}

	@Override
	public String toString() {
		return key.getPublicKey().toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AccountReference)) {
			return false;
		}

		return ((AccountReference) o).key.equals(this.key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}
}
