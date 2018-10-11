package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.Token;

public class AtomFeeConsumable extends Consumable {
	private final EUID service;
	public AtomFeeConsumable(long quantity, AccountReference address, long nonce, Token tokenReference, long planck) {
		super(quantity, address, nonce, tokenReference, planck, Spin.UP);

		this.service = new EUID(1);
	}
}
