package com.radixdlt.client.atommodel.timestamp;

import com.radixdlt.client.atommodel.quarks.ChronoQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.SerializerId2;

import java.util.Collections;
import java.util.Set;

/**
 * Particle which stores time related aspects of an atom.
 */
@SerializerId2("TIMESTAMPPARTICLE")
public class TimestampParticle extends Particle {
	private TimestampParticle() {
	}

	public TimestampParticle(long timestamp) {
		super(new ChronoQuark("default", timestamp));
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.emptySet();
	}

	public long getTimestamp() {
		return getQuarkOrError(ChronoQuark.class).getTimestamp();
	}
}
