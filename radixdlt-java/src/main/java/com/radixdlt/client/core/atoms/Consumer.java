package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class Consumer extends AbstractConsumable {
	public Consumer(long quantity, ECKeyPair owner, long nonce, EUID assetId) {
		super(quantity, Collections.singleton(owner), nonce, assetId);
	}

	public Consumer(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(quantity, owners, nonce, assetId);
	}

	@Override
	public long getSignedQuantity() {
		return -getQuantity();
	}

	public void addConsumerQuantities(long amount, Set<ECKeyPair> newOwners, Map<Set<ECKeyPair>, Long> consumerQuantities) {
		if (amount > getQuantity()) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getQuantity() + ")"
			);
		}

		if (amount == getQuantity()) {
			consumerQuantities.merge(newOwners, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwners, amount, Long::sum);
		consumerQuantities.merge(getOwners(), getQuantity() - amount, Long::sum);
	}
}
