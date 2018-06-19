package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.serialization.Dson;
import com.radixdlt.client.core.util.Hash;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Particle {
	private final Set<EUID> destinations;
	protected final Set<ECKeyPair> owners;

	Particle() {
		this.destinations = null;
		this.owners = null;
	}

	Particle(Set<EUID> destinations) {
		this.destinations = destinations;
		this.owners = null;
	}

	Particle(Set<EUID> destinations, Set<ECKeyPair> owners) {
		this.destinations = destinations;
		this.owners = owners;
	}

	public boolean hasOwner(ECPublicKey publicKey) {
		// TODO: optimize this
		return this.owners != null && this.owners.stream().map(ECKeyPair::getPublicKey).allMatch(pb -> pb.equals(publicKey));
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

	public Set<ECPublicKey> getOwners() {
		return owners.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet());
	}

	public Set<RadixAddress> getOwnersByAddress(RadixUniverseConfig universe) {
		return owners.stream().map(ecKeyPair -> new RadixAddress(universe, ecKeyPair.getPublicKey())).collect(Collectors.toSet());
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
}
