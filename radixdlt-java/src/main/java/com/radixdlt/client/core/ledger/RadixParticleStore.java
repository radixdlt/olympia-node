package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import io.reactivex.Observable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
					final List<SpunParticle> spunParticleList = observation.getAtom().spunParticles().collect(Collectors.toList());

					if (observation.getType() == Type.DELETE) {
						Collections.reverse(spunParticleList);
					}

					spunParticleList.stream()
						.filter(s -> s.getParticle().getShardables().contains(address))
						.forEach(s -> {
							final TransitionedParticle tp =
								TransitionedParticle.fromSpunParticle(s, observation.getType());

							final ParticleObservation p = ParticleObservation.ofTransitionedParticle(tp);
							emitter.onNext(p);
						});
				} else if (observation.isHead()) {
					emitter.onNext(ParticleObservation.head());
				}
			}))
		);
	}
}
