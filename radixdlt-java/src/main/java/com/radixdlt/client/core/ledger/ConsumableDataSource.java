package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.TransactionAtom;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class ConsumableDataSource implements ParticleStore {
	private final AtomStore atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<AbstractConsumable>> cache = new ConcurrentHashMap<>();

	public ConsumableDataSource(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<AbstractConsumable> getConsumables(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr -> atomStore.getAtoms(address)
			.filter(Atom::isTransactionAtom)
			.map(Atom::getAsTransactionAtom)
			.flatMapIterable(TransactionAtom::getAbstractConsumables)
			.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.cache()
			//.replay(1)
			//.autoConnect()
		);
	}
}
