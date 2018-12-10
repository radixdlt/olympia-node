package com.radixdlt.client.atommodel.unique;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.ParticleIndex;
import org.radix.serialization2.SerializerId2;

@SerializerId2("UNIQUEID")
public class UniqueId extends ParticleIndex {

	private UniqueId() {
	}

	public UniqueId(RadixAddress address, String unique) {
		super(address, unique);
	}

	@Override
	public String toString() {
		return this.getAddress().toString() + "/unique/" + this.getUnique();
	}
}
