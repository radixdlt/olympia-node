package com.radixdlt.client.atommodel.unique;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

@SerializerId2("UNIQUEIDPARTICLE")
public class UniqueParticle extends Particle {
	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private String name;

	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	private UniqueParticle() {
		super();
	}

	public UniqueParticle(RadixAddress address, String unique) {
		this.address = address;
		this.name = unique;
	}

	public String getName() {
		return name;
	}

	public RadixResourceIdentifer getRri() {
		return new RadixResourceIdentifer(address, "unique", name);
	}
}
