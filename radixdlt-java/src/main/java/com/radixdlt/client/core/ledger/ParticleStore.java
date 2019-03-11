package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import io.reactivex.Observable;

/**
 * A store of particles.
 * A temporary interface until a more fleshed out reducer framework is used
 */
public interface ParticleStore {

	/**
	 * Retrieves particles for a given address
	 *
	 * @param address the address to retrieve the particles for
	 * @return unending observable of particle observations
	 */
	Observable<ParticleObservation> getParticles(RadixAddress address);
}
