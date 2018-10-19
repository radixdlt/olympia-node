package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.atoms.particles.quarks.ChronoQuark;
import com.radixdlt.client.core.crypto.ECPublicKey;

import java.util.Collections;
import java.util.Set;

/**
 * Particle which stores time related aspects of an atom.
 */
public class TimestampParticle extends Particle {
	public TimestampParticle(long timestamp) {
		super(Spin.UP, new ChronoQuark("default", timestamp));
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.emptySet();
	}

	public Long getTimestamp() {
		return quarks(ChronoQuark.class).filter(q
				-> q.getTimestampKey().equals("default")).mapToLong(ChronoQuark::getTimestampValue).findFirst().getAsLong();
	}
}
