package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Particle;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class RadixParticleStore implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<Particle>> cache = new ConcurrentHashMap<>();

	public RadixParticleStore(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<Particle> getParticles(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.flatMapIterable(Atom::getParticles)
			.filter(particle -> particle.getAddresses().contains(address.getPublicKey()))
			.cache()
		);
	}
}
