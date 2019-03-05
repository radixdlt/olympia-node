package com.radixdlt.client.core.atoms.particles;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;
import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * A logical action on the ledger
 */
@SerializerId2("PARTICLE")
public abstract class Particle extends SerializableObject {
	protected Particle() {
	}

	public final Set<ECPublicKey> getKeyDestinations() {
		Set<RadixAddress> addresses = new HashSet<>();

		if (this instanceof Accountable) {
			Accountable a = (Accountable) this;
			addresses.addAll(a.getAddresses());
		}

		if (this instanceof Identifiable) {
			Identifiable i = (Identifiable) this;
			addresses.add(i.getRRI().getAddress());
		}

		return addresses.stream().map(RadixAddress::getPublicKey).collect(Collectors.toSet());
	}

	public final byte[] toDson() {
		return Serialize.getInstance().toDson(this, DsonOutput.Output.HASH);
	}

	public final RadixHash getHash() {
		return RadixHash.of(toDson());
	}

	public final EUID getHid() {
		return this.getHash().toEUID();
	}
}
