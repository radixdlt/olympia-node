package com.radixdlt.atommodel.unique;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;

@SerializerId2("radix.particles.unique")
public class UniqueParticle extends Particle {
	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private String name;

	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	UniqueParticle() {
		// For serializer
		super();
	}

	public UniqueParticle(String name, RadixAddress address, long nonce) {
		super(address.getUID());
		this.name = Objects.requireNonNull(name);
		this.address = address;
		this.nonce = nonce;
	}

	public RRI getRRI() {
		return RRI.of(address, name);
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), String.valueOf(name), String.valueOf(address));
	}
}
