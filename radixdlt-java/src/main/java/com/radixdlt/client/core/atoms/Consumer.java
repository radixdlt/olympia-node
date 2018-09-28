package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Consumer extends AbstractConsumable {
	public Consumer(long quantity, List<AccountReference> addresses, long nonce, EUID assetId, long planck) {
		super(quantity, addresses, nonce, assetId, planck);
	}

	@Override
	public long getSignedQuantity() {
		return -getAmount();
	}

	public void addConsumerQuantities(long amount, Set<ECKeyPair> newOwners, Map<Set<ECKeyPair>, Long> consumerQuantities) {
		if (amount > getAmount()) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount == getAmount()) {
			consumerQuantities.merge(newOwners, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwners, amount, Long::sum);
		consumerQuantities.merge(getOwners(), getAmount() - amount, Long::sum);
	}
}
