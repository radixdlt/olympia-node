package com.radixdlt.client.application.translate;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.Consumable;
import io.reactivex.Observable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ConsumableDataSource {
	private final Function<EUID, Observable<Atom>> atomStore;
	private final ConcurrentHashMap<RadixAddress, Observable<Collection<Consumable>>> cache = new ConcurrentHashMap<>();

	public ConsumableDataSource(Function<EUID, Observable<Atom>> atomStore) {
		this.atomStore = atomStore;
	}

	public Observable<Collection<Consumable>> getConsumables(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr ->
			Observable.<Collection<Consumable>>just(Collections.emptySet()).concatWith(
				Observable.combineLatest(
					Observable.fromCallable(() -> new TransactionAtoms(address, Asset.TEST.getId())),
					atomStore.apply(address.getUID())
					.filter(Atom::isTransactionAtom)
					.map(Atom::getAsTransactionAtom),
					(transactionAtoms, atom) ->
						transactionAtoms.accept(atom)
							.getUnconsumedConsumables()
				).flatMapMaybe(unconsumedMaybe -> unconsumedMaybe)
			).debounce(1000, TimeUnit.MILLISECONDS)
				.replay(1).autoConnect()
		);
	}
}
