package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import io.reactivex.Observable;

public interface ParticleStore {
	Observable<Particle> getParticles(RadixAddress address);
}
