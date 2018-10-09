package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Spin;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.observables.ConnectableObservable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionAtoms {
	private static final Logger LOGGER = LoggerFactory.getLogger(TransactionAtoms.class);

	public class TransactionAtomsUpdate {
		private final Observable<Atom> newValidTransactions;

		private TransactionAtomsUpdate(Observable<Atom> newValidTransactions) {
			this.newValidTransactions = newValidTransactions;
		}

		public Observable<Atom> getNewValidTransactions() {
			return newValidTransactions;
		}

		public Maybe<Collection<Consumable>> getUnconsumedConsumables() {
			return newValidTransactions.lastElement().map(lastTx -> unconsumedConsumables.values());
		}
	}

	private final RadixAddress address;
	private final EUID assetId;
	private final ConcurrentHashMap<ByteBuffer, Consumable> unconsumedConsumables = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ByteBuffer, Atom> missingConsumable = new ConcurrentHashMap<>();

	public TransactionAtoms(RadixAddress address, EUID assetId) {
		this.address = address;
		this.assetId = assetId;
	}

	private void addConsumables(Atom atom, ObservableEmitter<Atom> emitter) {
		atom.getConsumables(Spin.DOWN).stream()
			.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.filter(particle -> particle.getTokenClass().equals(assetId))
			.forEach(down -> {
				ByteBuffer dson = ByteBuffer.wrap(down.getDson());
				Consumable up = unconsumedConsumables.remove(dson);
				if (up == null) {
					throw new IllegalStateException("Missing consumable for consumer.");
				}
			});

		atom.getConsumables(Spin.UP).stream()
			.filter(up -> up.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.filter(up -> up.getTokenClass().equals(assetId))
			.forEach(up -> {
				ByteBuffer dson = ByteBuffer.wrap(up.getDson());
				unconsumedConsumables.compute(dson, (thisHash, current) -> {
					if (current == null) {
						return up;
					} else {
						throw new IllegalStateException("Consumable already exists.");
					}
				});

				Atom reanalyzeAtom = missingConsumable.remove(dson);
				if (reanalyzeAtom != null) {
					checkConsumers(reanalyzeAtom, emitter);
				}
			});
	}

	private void checkConsumers(Atom transactionAtom, ObservableEmitter<Atom> emitter) {
		Optional<ByteBuffer> missing = transactionAtom.getConsumables(Spin.DOWN).stream()
			.filter(down -> down.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.filter(down -> down.getTokenClass().equals(assetId))
			.map(Consumable::getDson)
			.map(ByteBuffer::wrap)
			.filter(dson -> !unconsumedConsumables.containsKey(dson))
			.findFirst();

		if (missing.isPresent()) {
			LOGGER.info("Missing consumable for atom: " + transactionAtom);

			missingConsumable.compute(missing.get(), (thisHash, current) -> {
				if (current == null) {
					return transactionAtom;
				} else {
					throw new IllegalStateException();
				}
			});
		} else {
			if (transactionAtom.getConsumables(Spin.UP).stream().allMatch(p -> p instanceof AtomFeeConsumable))  {
				return;
			}

			emitter.onNext(transactionAtom);
			addConsumables(transactionAtom, emitter);
		}
	}

	public TransactionAtomsUpdate accept(Atom transactionAtom) {
		ConnectableObservable<Atom> observable =
			Observable.<Atom>create(emitter -> {
				synchronized (TransactionAtoms.this) {
					checkConsumers(transactionAtom, emitter);
				}
				emitter.onComplete();
			}).replay();

		observable.connect();

		return new TransactionAtomsUpdate(observable);
	}
}
