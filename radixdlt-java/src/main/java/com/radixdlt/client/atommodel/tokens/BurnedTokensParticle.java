package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 *  A particle which represents an amount of consuming, burned fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("BURNEDTOKENSPARTICLE")
public final class BurnedTokensParticle extends Particle implements Accountable, Ownable, Fungible, ConsumingTokens {
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenTypeReference")
	@DsonOutput(Output.ALL)
	private RadixResourceIdentifer tokenTypeReference;

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

	protected BurnedTokensParticle() {
	}

	public BurnedTokensParticle(UInt256 amount, UInt256 granularity, RadixAddress address, long nonce,
	                            TokenTypeReference tokenTypeReference, long planck) {
		super();

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.address = address;
		this.tokenTypeReference = new RadixResourceIdentifer(tokenTypeReference.getAddress(), "tokenclasses", tokenTypeReference.getSymbol());
		this.granularity = granularity;
		this.planck = planck;
		this.nonce = nonce;
		this.amount = amount;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(address);
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public FungibleType getType() {
		return FungibleType.BURNED;
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
	public TokenTypeReference getTokenTypeReference() {
		return TokenTypeReference.of(tokenTypeReference.getAddress(), tokenTypeReference.getUnique());
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return Collections.singleton(this.address.getPublicKey());
	}

	@Override
	public ECPublicKey getOwner() {
		return this.address.getPublicKey();
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenTypeReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			String.valueOf(address),
			planck,
			nonce);
	}
}
