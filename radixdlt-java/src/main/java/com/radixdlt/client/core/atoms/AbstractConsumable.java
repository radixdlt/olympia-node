package com.radixdlt.client.core.atoms;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.serialization.Dson;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractConsumable extends Particle {
	private final Set<EUID> destinations;
	private final Set<ECKeyPair> owners;
	private final long quantity;
	private final long nonce;
	@SerializedName("asset_id")
	private final EUID assetId;

	AbstractConsumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(1);

		this.destinations = owners.stream().map(ECKeyPair::getUID).collect(Collectors.toSet());
		this.owners = owners;
		this.quantity = quantity;
		this.nonce = nonce;
		this.assetId = assetId;
	}

	public long getNonce() {
		return nonce;
	}

	public EUID getAssetId() {
		return assetId;
	}

	public long getQuantity() {
		return quantity;
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	public Set<ECPublicKey> getOwnersPublicKeys() {
		return owners == null ? Collections.emptySet() : owners.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet());
	}

	public Set<ECKeyPair> getOwners() {
		return owners;
	}

	public boolean isAbstractConsumable() {
		return this instanceof AbstractConsumable;
	}

	public boolean isConsumable() {
		return this instanceof Consumable;
	}

	public boolean isConsumer() {
		return this instanceof Consumer;
	}

	public Consumer getAsConsumer() {
		return (Consumer) this;
	}

	public Consumable getAsConsumable() {
		return (Consumable) this;
	}

	public AbstractConsumable getAsAbstractConsumable() {
		return (AbstractConsumable) this;
	}

	public RadixHash getHash() {
		return RadixHash.of(getDson());
	}

	public byte[] getDson() {
		return Dson.getInstance().toDson(this);
	}

	@Override
	public String toString() {
		return this.getClass().getName() + " owners(" + owners + ")";
	}

	public abstract long getSignedQuantity();
}
