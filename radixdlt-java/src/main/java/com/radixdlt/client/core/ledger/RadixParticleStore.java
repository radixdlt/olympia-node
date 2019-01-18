package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RadixParticleStore implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<SpunParticle>> cache = new ConcurrentHashMap<>();

	public RadixParticleStore(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	@Override
	public Observable<SpunParticle> getParticles(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.filter(AtomObservation::isStore)
			.map(AtomObservation::getAtom)
			.flatMapIterable(a -> a.spunParticles().collect(Collectors.toList()))
			.filter(s -> s.getParticle().getAddresses().contains(address.getPublicKey()))
			.cache()
		);
	}
}
