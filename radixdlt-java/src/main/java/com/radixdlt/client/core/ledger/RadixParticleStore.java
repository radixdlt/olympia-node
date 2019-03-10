package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import io.reactivex.Observable;

import java.util.concurrent.ConcurrentHashMap;

public class RadixParticleStore implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<ParticleObservation>> cache = new ConcurrentHashMap<>();

	public RadixParticleStore(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	@Override
	public Observable<ParticleObservation> getParticles(RadixAddress address) {
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.flatMap(observation -> Observable.create(emitter -> {
				if (observation.hasAtom()) {
					observation.getAtom().spunParticles().filter(s -> s.getParticle().getShardables().contains(address))
						.forEach(s -> {
							final TransitionedParticle tp =
								TransitionedParticle.fromSpunParticle(s, observation.getType());

							final ParticleObservation p = ParticleObservation.ofParticle(tp);
							emitter.onNext(p);
						});
				} else if (observation.isHead()) {
					emitter.onNext(ParticleObservation.head());
				}
			}))
		);
	}
}
