package com.radixdlt.client.application.translate;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.ledger.RadixLedger;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ConsumableDataSource {
	private final RadixLedger ledger;
	private final ConcurrentHashMap<RadixAddress, Observable<Collection<Consumable>>> cache = new ConcurrentHashMap<>();

	public ConsumableDataSource(RadixLedger ledger) {
		this.ledger = ledger;
	}

	public Single<Collection<Consumable>> getCurrentConsumables(RadixAddress address) {
		return this.getConsumables(address).firstOrError();
	}

	public Observable<Collection<Consumable>> getConsumables(RadixAddress address) {
		// TODO: use https://github.com/JakeWharton/RxReplayingShare to disconnect when unsubscribed
		return cache.computeIfAbsent(address, addr ->
			Observable.<Collection<Consumable>>just(Collections.emptySet()).concatWith(
				Observable.combineLatest(
					Observable.fromCallable(() -> new TransactionAtoms(address, Asset.XRD.getId())),
					ledger.getAllAtoms(address.getUID(), Atom.class),
					(transactionAtoms, atom) ->
						transactionAtoms.accept(atom)
							.getUnconsumedConsumables()
				).flatMapMaybe(unconsumedMaybe -> unconsumedMaybe)
			).debounce(1000, TimeUnit.MILLISECONDS)
				.replay(1).autoConnect()
		);
	}
}
