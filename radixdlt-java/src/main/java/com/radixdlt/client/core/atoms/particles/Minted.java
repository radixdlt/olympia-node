package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.atoms.AccountReference;

public class Minted extends Consumable {
	public Minted(long quantity, AccountReference address, long nonce, String tokenReference, long planck) {
		super(quantity, address, nonce, tokenReference, planck, Spin.UP);
	}
}
