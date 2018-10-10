package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AccountReference;

public class AtomFeeConsumable extends Consumable {
	private final EUID service;
	public AtomFeeConsumable(long quantity, AccountReference address, long nonce, EUID assetId, long planck) {
		super(quantity, address, nonce, assetId, planck, Spin.UP);

		this.service = new EUID(1);
	}
}
