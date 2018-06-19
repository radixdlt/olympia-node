package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.address.EUID;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractConsumable extends Particle {
	protected final long quantity;
	protected final long nonce;
	protected final EUID asset_id;

	AbstractConsumable(long quantity, Set<ECKeyPair> owners, long nonce, EUID asset_id) {
		super(owners.stream().map(ECKeyPair::getUID).collect(Collectors.toSet()), owners);
		this.quantity = quantity;
		this.nonce = nonce;
		this.asset_id = asset_id;
	}

	public long getNonce() {
		return nonce;
	}

	public EUID getAssetId() {
		return asset_id;
	}

	public long quantity() {
		return quantity;
	}

	public abstract long signedQuantity();
}
