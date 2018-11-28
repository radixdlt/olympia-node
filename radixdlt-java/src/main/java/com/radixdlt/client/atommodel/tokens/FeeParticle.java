package com.radixdlt.client.atommodel.tokens;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;

@SerializerId2("FEEPARTICLE")
public class FeeParticle extends OwnedTokensParticle {
	@JsonProperty("service")
	@DsonOutput(DsonOutput.Output.ALL)
	private EUID service;

	private FeeParticle() {
	}

	public FeeParticle(UInt256 quantity, RadixAddress address, long nonce, TokenClassReference tokenRef, long planck) {
		super(quantity, FungibleQuark.FungibleType.MINTED, address, nonce, tokenRef, planck);

		this.service = new EUID(1);
	}
}
