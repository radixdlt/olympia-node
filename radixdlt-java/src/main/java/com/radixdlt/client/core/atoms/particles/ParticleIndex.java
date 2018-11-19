package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.SerializableObject;

public abstract class ParticleIndex extends SerializableObject {
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	protected ParticleIndex() {
	}

	protected ParticleIndex(RadixAddress address) {
		this.address = address;
	}

	public RadixAddress getAddress() {
		return address;
	}
}
