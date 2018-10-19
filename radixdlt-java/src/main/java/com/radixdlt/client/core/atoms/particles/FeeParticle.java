package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.quarks.FungibleQuark;
import org.radix.common.ID.EUID;

public class FeeParticle extends TransferParticle {
	private final EUID service;

	public FeeParticle(long quantity, AccountReference address, long nonce, TokenClassReference tokenRef, long planck) {
		super(quantity, FungibleQuark.FungibleType.MINTED, address, nonce, tokenRef, planck, Spin.UP);

		this.service = new EUID(1);
	}
}
