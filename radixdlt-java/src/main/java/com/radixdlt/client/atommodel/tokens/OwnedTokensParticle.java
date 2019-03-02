package com.radixdlt.client.atommodel.tokens;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 *  A particle which represents an amount of fungible tokens owned by some key owner and stored in an account.
 */
@SerializerId2("OWNEDTOKENSPARTICLE")
public class OwnedTokensParticle extends Particle implements Accountable, Ownable, Fungible {
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("token_reference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixResourceIdentifer tokenClassReference;

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

	private FungibleType type;

	protected OwnedTokensParticle() {
	}

	public OwnedTokensParticle(UInt256 amount, UInt256 granularity, FungibleType type, RadixAddress address, long nonce,
	                           TokenTypeReference tokenRef, long planck) {
		super();

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.address = address;
		this.tokenClassReference = new RadixResourceIdentifer(tokenRef.getAddress(), "tokenclasses", tokenRef.getSymbol());
		this.granularity = granularity;
		this.planck = planck;
		this.nonce = nonce;
		this.amount = amount;
		this.type = type;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(address);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public void addConsumerQuantities(UInt256 amount, ECKeyPair newOwner, Map<ECKeyPair, UInt256> consumerQuantities) {
		if (amount.compareTo(getAmount()) > 0) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount.equals(getAmount())) {
			consumerQuantities.merge(newOwner, amount, UInt256::add);
			return;
		}

		consumerQuantities.merge(newOwner, amount, UInt256::add);
		consumerQuantities.merge(getAddress().toECKeyPair(), getAmount().subtract(amount), UInt256::add);
	}

	@Override
	public FungibleType getType() {
		return this.type;
	}

	@Override
	public long getPlanck() {
		return this.planck;
	}

	@Override
	public long getNonce() {
		return this.nonce;
	}

	public TokenTypeReference getTokenClassReference() {
		return TokenTypeReference.of(tokenClassReference.getAddress(), tokenClassReference.getUnique());
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

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenClassReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			String.valueOf(address),
			planck,
			nonce,
			String.valueOf(type));
	}

	@JsonProperty("type")
	@DsonOutput(DsonOutput.Output.ALL)
	private String getJsonType() {
		return this.type == null ? null : this.type.name().toLowerCase();
	}

	@JsonProperty("type")
	private void setJsonType(String type) {
		this.type = type == null ? null : FungibleType.valueOf(type.toUpperCase());
	}
}
