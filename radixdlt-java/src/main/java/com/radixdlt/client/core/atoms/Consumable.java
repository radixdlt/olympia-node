package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.Set;

public class Consumable extends AbstractConsumable {
	public Consumable(long quantity, ECKeyPair owner, long nonce, EUID asset_id) {
		super(quantity, Collections.singleton(owner), nonce, asset_id);
	}

	public Consumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID asset_id) {
		super(quantity, owners, nonce, asset_id);
	}

	@Override
	public long signedQuantity() {
		return quantity();
	}

	public Consumer toConsumer() {
		return new Consumer(quantity, owners, nonce, asset_id);
	}
}
