package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.util.List;

public class AtomFeeConsumable extends Consumable {
	private final EUID service;
	public AtomFeeConsumable(long quantity, List<AccountReference> addresses, long nonce, EUID assetId, long planck) {
		super(quantity, addresses, nonce, assetId, planck);

		this.service = new EUID(1);
	}
}
