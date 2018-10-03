package com.radixdlt.client.core.ledger;

import com.radixdlt.client.application.translate.TransactionAtoms;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.TransactionAtom;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ConsumableDataSource implements ParticleStore {
	private final Function<EUID, Observable<AtomObservation>> atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<Collection<Consumable>>> cache = new ConcurrentHashMap<>();

	public ConsumableDataSource(Function<EUID, Observable<AtomObservation>> atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<Collection<Consumable>> getConsumables(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr ->
			Observable.combineLatest(
				Observable.fromCallable(() -> new TransactionAtoms(address, Asset.TEST.getId())),
				atomStore.apply(address.getUID()).filter(o -> o.isHead() || o.getAtom().isTransactionAtom()),
				(transactionAtoms, atomObservation) -> {
					if (atomObservation.isHead()) {
						return Maybe.just(transactionAtoms.getUnconsumedConsumables());
					} else {
						TransactionAtom atom = atomObservation.getAtom().getAsTransactionAtom();
						transactionAtoms.accept(atom);
						return Maybe.<Collection<Consumable>>empty();
					}
				}
			).flatMapMaybe(unconsumedMaybe -> unconsumedMaybe)
			.replay(1).autoConnect()
		);
	}
}
