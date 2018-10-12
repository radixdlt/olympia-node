package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.TokenRef;

public class AtomFeeConsumable extends Consumable {
	private final EUID service;
	public AtomFeeConsumable(long quantity, AccountReference address, long nonce, TokenRef tokenRef, long planck) {
		super(quantity, address, nonce, tokenRef, planck, Spin.UP);

		this.service = new EUID(1);
	}
}
