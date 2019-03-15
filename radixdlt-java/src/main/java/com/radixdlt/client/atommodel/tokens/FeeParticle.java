package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

@SerializerId2("FEEPARTICLE")
public class FeeParticle extends Particle implements ConsumableTokens {
	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenTypeReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixResourceIdentifer tokenTypeReference;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("planck")
	@DsonOutput(DsonOutput.Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	@JsonProperty("service")
	@DsonOutput(DsonOutput.Output.ALL)
	private EUID service;

	private FeeParticle() {
	}

	public FeeParticle(UInt256 amount, RadixAddress address, long nonce, TokenDefinitionReference tokenDefinitionReference, long planck) {
		this.address = address;
		this.tokenTypeReference = new RadixResourceIdentifer(tokenDefinitionReference.getAddress(), "tokenclasses", tokenDefinitionReference.getSymbol());
		// FIXME RLAU-40 Check if the hard-coded granularity here is valid
		this.granularity = UInt256.ONE;
		this.planck = planck;
		this.nonce = nonce;
		this.amount = amount;
		this.service = new EUID(1);
	}

	@Override
	public TokenDefinitionReference getTokenTypeReference() {
		return TokenDefinitionReference.of(tokenTypeReference.getAddress(), tokenTypeReference.getUnique());
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public UInt256 getGranularity() {
		return this.granularity;
	}

	@Override
	public long getPlanck() {
		return this.planck;
	}

	@Override
	public long getNonce() {
		return this.nonce;
	}

	@Override
	public ECPublicKey getOwner() {
		return this.address.getPublicKey();
	}
}
