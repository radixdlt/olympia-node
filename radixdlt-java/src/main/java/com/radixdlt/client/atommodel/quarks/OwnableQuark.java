package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import java.util.Objects;

/**
 * A quark that protects a particle from being spun DOWN unless it was signed by the owner
 */
@SerializerId2("OWNABLEQUARK")
public final class OwnableQuark extends Quark {
	@JsonProperty("owner")
	@DsonOutput(Output.ALL)
	private ECPublicKey owner;

	private OwnableQuark() {
	}

	public OwnableQuark(ECPublicKey owner) {
		this.owner = Objects.requireNonNull(owner);
	}

	public ECPublicKey getOwner() {
		return this.owner;
	}
}
