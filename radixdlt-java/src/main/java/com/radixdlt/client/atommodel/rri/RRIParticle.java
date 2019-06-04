package com.radixdlt.client.atommodel.rri;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

@SerializerId2("radix.particles.rri")
public class RRIParticle extends Particle implements Accountable {
	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI rri;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	private RRIParticle() {
	}

	public RRIParticle(RRI rri) {
		super(rri.getAddress().getUID());

		this.rri = Objects.requireNonNull(rri);
		this.nonce = 0;
	}

	public RRI getRRI() {
		return rri;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(rri.getAddress());
	}

	public long getNonce() {
		return nonce;
	}

	public String toString() {
		return String.format("%s[(%s:%s)]",
			getClass().getSimpleName(), rri, nonce);
	}
}
