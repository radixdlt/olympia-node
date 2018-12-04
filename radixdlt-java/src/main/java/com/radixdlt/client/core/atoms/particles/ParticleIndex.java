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

	@JsonProperty("unique")
	@DsonOutput(Output.ALL)
	private String unique;

	protected ParticleIndex() {
	}

	protected ParticleIndex(RadixAddress address, String unique) {
		this.address = address;
		this.unique = unique;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}
}
