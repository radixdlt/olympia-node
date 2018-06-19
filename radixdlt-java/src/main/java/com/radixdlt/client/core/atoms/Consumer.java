package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class Consumer extends AbstractConsumable {
	public Consumer(long quantity, ECKeyPair owner, long nonce, EUID asset_id) {
		super(quantity, Collections.singleton(owner), nonce, asset_id);
	}

	public Consumer(long quantity, Set<ECKeyPair> owners, long nonce, EUID asset_id) {
		super(quantity, owners, nonce, asset_id);
	}

	@Override
	public long signedQuantity() {
		return -quantity();
	}

	public void addConsumerQuantities(long amount, Set<ECKeyPair> newOwners, Map<Set<ECKeyPair>, Long> consumerQuantities) {
		if (amount > quantity) {
			throw new IllegalArgumentException("Unable to create consumable with amount " + amount + " (available: " + quantity + ")");
		}

		if (amount == quantity) {
			consumerQuantities.merge(newOwners, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwners, amount, Long::sum);
		consumerQuantities.merge(owners, quantity - amount, Long::sum);
	}
}
