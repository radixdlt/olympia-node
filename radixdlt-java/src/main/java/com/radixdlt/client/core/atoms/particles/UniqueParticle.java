package com.radixdlt.client.core.atoms.particles;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;

@SerializerId2("UNIQUEPARTICLE")
public class UniqueParticle extends Particle {

	@JsonProperty("unique")
	@DsonOutput(Output.ALL)
	private Payload unique;

	@JsonProperty("destinations")
	@DsonOutput(Output.ALL)
	private Set<EUID> destinations;

	@JsonProperty("owners")
	@DsonOutput(Output.ALL)
	private Set<ECKeyPair> owners;

	private Spin spin;

	UniqueParticle() {
		// No-arg constructor for serialization
	}

	// TODO: make immutable
	public UniqueParticle(Payload unique, Set<EUID> destinations, Set<ECKeyPair> owners) {
		Objects.requireNonNull(unique);

		this.spin = Spin.UP;
		this.destinations = destinations;
		this.owners = owners;
		this.unique = unique;
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return owners.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet());
	}

	@Override
	public Spin getSpin() {
		return spin;
	}

	public static UniqueParticle create(Payload unique, ECPublicKey key) {
		return new UniqueParticle(unique, Collections.singleton(key.getUID()), Collections.singleton(key.toECKeyPair()));
	}

	@JsonProperty("spin")
	@DsonOutput(value = {Output.WIRE, Output.API, Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}
}
