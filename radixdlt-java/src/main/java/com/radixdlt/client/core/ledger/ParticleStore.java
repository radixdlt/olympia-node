package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

public interface ParticleStore {
	Observable<SpunParticle> getParticles(RadixAddress address);
}
