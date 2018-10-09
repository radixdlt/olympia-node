package com.radixdlt.client.core.atoms.particles;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.TokenClassReference;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Consumable implements Particle {
	private final List<AccountReference> addresses;
	private final long amount;
	private final long nonce;
	private final Spin spin;
	private final long planck;
	@SerializedName("token_reference")
	private final TokenClassReference tokenClassReference;

	public Consumable(long amount, List<AccountReference> addresses, long nonce, EUID tokenId, long planck, Spin spin) {
		this.spin = spin;
		this.addresses = addresses;
		this.amount = amount;
		this.nonce = nonce;
		this.tokenClassReference = new TokenClassReference(tokenId, new EUID(0));
		this.planck = planck;
	}

	public Consumable spinDown() {
		return new Consumable(getAmount(), getAddresses(), getNonce(), getTokenClass(), getPlanck(), Spin.DOWN);
	}

	public void addConsumerQuantities(long amount, Set<ECKeyPair> newOwners, Map<Set<ECKeyPair>, Long> consumerQuantities) {
		if (amount > getAmount()) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + amount + " (available: " + getAmount() + ")"
			);
		}

		if (amount == getAmount()) {
			consumerQuantities.merge(newOwners, amount, Long::sum);
			return;
		}

		consumerQuantities.merge(newOwners, amount, Long::sum);
		consumerQuantities.merge(getOwners(), getAmount() - amount, Long::sum);
	}

	public List<AccountReference> getAddresses() {
		return addresses;
	}

	public long getPlanck() {
		return planck;
	}

	public Spin getSpin() {
		return spin;
	}

	public long getNonce() {
		return nonce;
	}

	public EUID getTokenClass() {
		return tokenClassReference.getToken();
	}

	public long getSignedAmount() {
		return amount * (getSpin() == Spin.UP ? 1 : -1);
	}

	public long getAmount() {
		return amount;
	}

	public Set<EUID> getDestinations() {
		return getOwnersPublicKeys().stream().map(ECPublicKey::getUID).collect(Collectors.toSet());
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return addresses == null ? Collections.emptySet() : addresses.stream().map(AccountReference::getKey).collect(Collectors.toSet());
	}

	public Set<ECKeyPair> getOwners() {
		return getOwnersPublicKeys().stream().map(ECPublicKey::toECKeyPair).collect(Collectors.toSet());
	}

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Dson.getInstance().toDson(this);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " owners(" + addresses + ") amount(" + amount + ") spin(" + spin + ")";
	}
}
