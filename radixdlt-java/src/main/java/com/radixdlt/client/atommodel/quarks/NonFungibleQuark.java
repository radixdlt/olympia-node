package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.ParticleIndex;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * A quark that makes a particle non fungible: only one particle with a given ID of its type can exist.
 */
@SerializerId2("NONFUNGIBLEQUARK")
public final class NonFungibleQuark extends Quark {
	@JsonProperty("index")
	@DsonOutput(DsonOutput.Output.ALL)
	private ParticleIndex index;

	private NonFungibleQuark() {
	}

	public NonFungibleQuark(ParticleIndex index) {
		this.index = index;
	}

	public ParticleIndex getIndex() {
		return index;
	}
}
