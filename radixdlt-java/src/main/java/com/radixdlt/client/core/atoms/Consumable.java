package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.util.List;

public class Consumable extends AbstractConsumable {
	public Consumable(long quantity, List<AccountReference> addresses, long nonce, EUID assetId, long planck) {
		super(quantity, addresses, nonce, assetId, planck);
	}

	@Override
	public long getSignedQuantity() {
		return getAmount();
	}

	public Consumer toConsumer() {
		return new Consumer(getAmount(), getAddresses(), getNonce(), getTokenClass(), getPlanck());
	}
}
