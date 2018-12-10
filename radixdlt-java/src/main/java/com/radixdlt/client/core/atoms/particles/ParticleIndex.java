package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;
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

	@Override
	public int hashCode() {
		return Objects.hash(this.getClass(), address, unique);
	}

	@Override
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass())) {
			return false;
		}

		ParticleIndex particleIndex = (ParticleIndex) o;
		return this.getAddress().equals(particleIndex.getAddress()) && this.getUnique().equals(particleIndex.getUnique());
	}
}
