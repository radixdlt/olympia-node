package com.radixdlt.client.core.atoms;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.TokenClassReference;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractConsumable implements Particle {
	private final List<AccountReference> addresses;
	private final long amount;
	private final long nonce;
	private final long spin;
	private final long planck;
	@SerializedName("token_reference")
	private final TokenClassReference tokenClassReference;

	AbstractConsumable(long amount, List<AccountReference> addresses, long nonce, EUID tokenReference, long planck, long spin) {
		this.spin = spin;
		this.addresses = addresses;
		this.amount = amount;
		this.nonce = nonce;
		this.tokenClassReference = new TokenClassReference(tokenReference, new EUID(0));
		this.planck = planck;
	}

	public List<AccountReference> getAddresses() {
		return addresses;
	}

	public long getPlanck() {
		return planck;
	}

	public long getSpin() {
		return spin;
	}

	public long getNonce() {
		return nonce;
	}

	public EUID getTokenClass() {
		return tokenClassReference.getToken();
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
		return this.getClass().getName() + " owners(" + addresses + ")";
	}

	public abstract long getSignedQuantity();
}
