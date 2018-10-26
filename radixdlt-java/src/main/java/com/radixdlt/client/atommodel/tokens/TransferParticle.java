package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.atommodel.quarks.AddressableQuark;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.quarks.OwnableQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SerializerId2("TRANSFERPARTICLE")
public class TransferParticle extends Particle {
	@JsonProperty("token_reference")
	@DsonOutput(DsonOutput.Output.ALL)
	private TokenClassReference tokenClassReference;

	protected TransferParticle() {
	}

	public TransferParticle(long amount, FungibleQuark.FungibleType type, RadixAddress address, long nonce,
	                        TokenClassReference tokenRef, long planck) {
		super(new OwnableQuark(address.getPublicKey()), new AddressableQuark(address),
				new FungibleQuark(amount, planck, nonce, type));

		this.tokenClassReference = tokenRef;
	}

	public RadixAddress getAddress() {
		return getQuarkOrError(AddressableQuark.class).getAddresses().get(0);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return getQuarkOrError(AddressableQuark.class).getAddresses().stream().map(RadixAddress::getPublicKey).collect(Collectors.toSet());
	}

	public void addConsumerQuantities(long amount, ECKeyPair newOwner, Map<ECKeyPair, Long> consumerQuantities) {
		if (amount > getAmount()) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount == getAmount()) {
			consumerQuantities.merge(newOwner, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwner, amount, Long::sum);
		consumerQuantities.merge(getAddress().toECKeyPair(), getAmount() - amount, Long::sum);
	}

	public FungibleQuark.FungibleType getType() {
		return getQuarkOrError(FungibleQuark.class).getType();
	}

	public long getPlanck() {
		return getQuarkOrError(FungibleQuark.class).getPlanck();
	}

	public long getNonce() {
		return getQuarkOrError(FungibleQuark.class).getNonce();
	}

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
	}

	public long getAmount() {
		return getQuarkOrError(FungibleQuark.class).getAmount();
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return Collections.singleton(getQuarkOrError(OwnableQuark.class).getOwner());
	}

	public ECPublicKey getOwner() {
		return getQuarkOrError(OwnableQuark.class).getOwner();
	}

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " owners(" + getQuarkOrError(OwnableQuark.class).getOwner() + ")"
				+ " amount(" + getQuarkOrError(FungibleQuark.class).getAmount() + ")";
	}
}
