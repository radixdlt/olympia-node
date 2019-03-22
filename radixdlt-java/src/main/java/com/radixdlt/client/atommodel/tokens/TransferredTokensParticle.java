package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 *  A particle which represents an amount of consumable and consuming, tranferable fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("TRANSFERREDTOKENSPARTICLE")
public final class TransferredTokensParticle extends Particle implements Accountable, Ownable, Fungible, ConsumingTokens, ConsumableTokens {
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(Output.ALL)
	private RadixResourceIdentifer tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(Output.ALL)
	private UInt256 amount;

	protected TransferredTokensParticle() {
	}

	public TransferredTokensParticle(UInt256 amount, UInt256 granularity, RadixAddress address, long nonce,
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

	@Override
	public RadixAddress getAddress() {
		return this.address;
	}

	@Override
	public long getPlanck() {
		return this.planck;
	}

	@Override
	public long getNonce() {
		return this.nonce;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return TokenDefinitionReference.of(tokenDefinitionReference.getAddress(), tokenDefinitionReference.getUnique());
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
}
