package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.Set;

public class Consumable extends AbstractConsumable {
	public Consumable(long quantity, ECKeyPair owner, long nonce, EUID assetId) {
		super(quantity, Collections.singleton(owner), nonce, assetId);
	}

	public Consumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(quantity, owners, nonce, assetId);
	}

	@Override
	public long getSignedQuantity() {
		return getQuantity();
	}

	public Consumer toConsumer() {
		return new Consumer(getQuantity(), getOwners(), getNonce(), getAssetId());
	}
}
