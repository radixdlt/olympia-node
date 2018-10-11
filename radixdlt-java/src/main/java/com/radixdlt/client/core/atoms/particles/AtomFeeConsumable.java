package com.radixdlt.client.core.atoms.particles;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.TokenRef;

@SerializerId2("FEEPARTICLE")
public class AtomFeeConsumable extends Consumable {
	@JsonProperty("service")
	@DsonOutput(Output.ALL)
	private final EUID service;

	public AtomFeeConsumable(long quantity, AccountReference address, long nonce, TokenRef tokenRef, long planck) {
		super(quantity, ConsumableType.MINTED, address, nonce, tokenRef, planck, Spin.UP);

		this.service = new EUID(1);
	}
}
