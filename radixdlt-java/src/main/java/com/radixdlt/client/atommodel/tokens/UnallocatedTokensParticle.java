package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("UNALLOCATEDTOKENSPARTICLE")
public class UnallocatedTokensParticle extends Particle implements Accountable, Ownable, Fungible {

	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixResourceIdentifer tokenDefinitionReference;

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

	public UnallocatedTokensParticle() {
		super();
	}

	public UnallocatedTokensParticle(UInt256 amount, UInt256 granularity, RadixAddress address, long nonce,
		TokenDefinitionReference tokenDefinitionReference, long planck) {
		super();

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.address = address;
		this.tokenDefinitionReference = new RadixResourceIdentifer(
			tokenDefinitionReference.getAddress(), "tokens", tokenDefinitionReference.getSymbol());
		this.granularity = granularity;
		this.planck = planck;
		this.nonce = nonce;
		this.amount = amount;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(this.address);
	}

	public RadixAddress getAddress() {
		return this.address;
	}

	public RadixResourceIdentifer getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return TokenDefinitionReference.of(tokenDefinitionReference.getAddress(), tokenDefinitionReference.getUnique());
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenDefinitionReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			String.valueOf(address),
			planck,
			nonce);
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public long getPlanck() {
		return this.planck;
	}

	@Override
	public long getNonce() {
		return this.nonce;
	}
}
