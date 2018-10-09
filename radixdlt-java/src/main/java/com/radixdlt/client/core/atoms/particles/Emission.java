package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AccountReference;
import java.util.List;

public class Emission extends Consumable {
	public Emission(long quantity, AccountReference address, long nonce, EUID assetId, long planck) {
		super(quantity, address, nonce, assetId, planck, Spin.UP);
	}
}
