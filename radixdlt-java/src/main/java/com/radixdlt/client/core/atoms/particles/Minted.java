package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.TokenRef;

public class Minted extends Consumable {
	public Minted(long quantity, AccountReference address, long nonce, TokenRef tokenRef, long planck) {
		super(quantity, address, nonce, tokenRef, planck, Spin.UP);
	}
}
