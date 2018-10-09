package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Consumable;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumableDataSource implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<Consumable>> cache = new ConcurrentHashMap<>();

	public ConsumableDataSource(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<Consumable> getConsumables(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.flatMapIterable(Atom::getConsumables)
			.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.cache()
			//.replay(1)
			//.autoConnect()
		);
	}
}
