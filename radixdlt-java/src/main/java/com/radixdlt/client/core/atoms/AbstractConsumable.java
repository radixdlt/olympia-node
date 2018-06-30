package com.radixdlt.client.core.atoms;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.address.EUID;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractConsumable extends Particle {
	private final long quantity;
	private final long nonce;
	@SerializedName("asset_id")
	private final EUID assetId;

	AbstractConsumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID assetId) {
		super(owners.stream().map(ECKeyPair::getUID).collect(Collectors.toSet()), owners);
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

	public abstract long getSignedQuantity();
}
