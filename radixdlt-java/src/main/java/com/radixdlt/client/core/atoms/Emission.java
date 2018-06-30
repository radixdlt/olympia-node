package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Set;

public class Emission extends Consumable {
	public Emission(long quantity, ECKeyPair owner, long nonce, EUID assetId) {
		super(quantity, owner, nonce, assetId);
	}

	public Emission(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(quantity, owners, nonce, assetId);
	}
}
