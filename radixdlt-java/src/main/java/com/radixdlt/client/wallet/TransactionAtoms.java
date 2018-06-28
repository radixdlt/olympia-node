package com.radixdlt.client.wallet;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.TransactionAtom;
import io.reactivex.ObservableEmitter;
import io.reactivex.observables.ConnectableObservable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionAtoms {
	private final static Logger logger = LoggerFactory.getLogger(TransactionAtoms.class);

	public class TransactionAtomsUpdate {
		private final io.reactivex.Observable<TransactionAtom> newValidTransactions;

		private TransactionAtomsUpdate(io.reactivex.Observable<TransactionAtom> newValidTransactions) {
			this.newValidTransactions = newValidTransactions;
		}

		public io.reactivex.Observable<TransactionAtom> getNewValidTransactions() {
			return newValidTransactions;
		}

		public io.reactivex.Maybe<Collection<Consumable>> getUnconsumedConsumables() {
			return newValidTransactions.lastElement().map(lastTx -> unconsumedConsumables.values());
		}
	}

	private final RadixAddress address;
	private final EUID assetId;
	private final ConcurrentHashMap<ByteBuffer, Consumable> unconsumedConsumables = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ByteBuffer, TransactionAtom> missingConsumable = new ConcurrentHashMap<>();

	TransactionAtoms(RadixAddress address, EUID assetId) {
		this.address = address;
		this.assetId = assetId;
	}

	private void addConsumables(TransactionAtom transactionAtom, ObservableEmitter<TransactionAtom> emitter) {
		transactionAtom.getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.filter(particle -> particle.getOwners().stream().allMatch(address::ownsKey))
			.filter(particle -> particle.getAssetId().equals(assetId))
			.forEach(particle -> {
				ByteBuffer dson = ByteBuffer.wrap(particle.getDson());
				if (particle.isConsumable()) {
					unconsumedConsumables.compute(dson, (thisHash, current) -> {
						if (current == null) {
							return particle.getAsConsumable();
						} else {
							throw new IllegalStateException();
						}
					});

					TransactionAtom reanalyzeAtom = missingConsumable.remove(dson);
					if (reanalyzeAtom != null) {
						checkConsumers(reanalyzeAtom, emitter);
					}
				} else {
					Consumable consumable = unconsumedConsumables.remove(dson);
					if (consumable == null) {
						throw new IllegalStateException();
					}
				}
			});
	}

	private void checkConsumers(TransactionAtom transactionAtom, ObservableEmitter<TransactionAtom> emitter) {
		Optional<ByteBuffer> missing = transactionAtom.getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.filter(particle -> particle.getOwners().stream().allMatch(address::ownsKey))
			.filter(particle -> particle.getAssetId().equals(assetId))
			.filter(AbstractConsumable::isConsumer)
			.map(AbstractConsumable::getDson)
			.map(ByteBuffer::wrap)
			.filter(dson -> !unconsumedConsumables.containsKey(dson))
			.findFirst();

		if (missing.isPresent()) {
			logger.info("Missing consumable for atom: " + transactionAtom);
			missingConsumable.compute(missing.get(), (thisHash, current) -> {
				if (current == null) {
					return transactionAtom;
				} else {
					throw new IllegalStateException();
				}
			});
		} else {
			emitter.onNext(transactionAtom);
			addConsumables(transactionAtom, emitter);
		}
	}

	public TransactionAtomsUpdate accept(TransactionAtom transactionAtom) {
		ConnectableObservable<TransactionAtom> observable = io.reactivex.Observable.<TransactionAtom>create(emitter -> {
			synchronized (TransactionAtoms.this) {
				checkConsumers(transactionAtom, emitter);
			}
			emitter.onComplete();
		}).replay();

		observable.connect();

		return new TransactionAtomsUpdate(observable);
	}
}
