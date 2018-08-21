package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.PayloadAtom;
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
		private final Observable<PayloadAtom> newValidTransactions;

		private TransactionAtomsUpdate(Observable<PayloadAtom> newValidTransactions) {
			this.newValidTransactions = newValidTransactions;
		}

		public Observable<PayloadAtom> getNewValidTransactions() {
			return newValidTransactions;
		}

		public Maybe<Collection<Consumable>> getUnconsumedConsumables() {
			return newValidTransactions.lastElement().map(lastTx -> unconsumedConsumables.values());
		}
	}

	private final RadixAddress address;
	private final EUID assetId;
	private final ConcurrentHashMap<ByteBuffer, Consumable> unconsumedConsumables = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ByteBuffer, PayloadAtom> missingConsumable = new ConcurrentHashMap<>();

	public TransactionAtoms(RadixAddress address, EUID assetId) {
		this.address = address;
		this.assetId = assetId;
	}

	private void addConsumables(PayloadAtom transactionAtom, ObservableEmitter<PayloadAtom> emitter) {
		transactionAtom.getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
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

					PayloadAtom reanalyzeAtom = missingConsumable.remove(dson);
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

	private void checkConsumers(PayloadAtom transactionAtom, ObservableEmitter<PayloadAtom> emitter) {
		Optional<ByteBuffer> missing = transactionAtom.getParticles().stream()
			.filter(Particle::isAbstractConsumable)
			.map(Particle::getAsAbstractConsumable)
			.filter(particle -> particle.getOwnersPublicKeys().stream().allMatch(address::ownsKey))
			.filter(particle -> particle.getAssetId().equals(assetId))
			.filter(AbstractConsumable::isConsumer)
			.map(AbstractConsumable::getDson)
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
			emitter.onNext(transactionAtom);
			addConsumables(transactionAtom, emitter);
		}
	}

	public TransactionAtomsUpdate accept(PayloadAtom transactionAtom) {
		ConnectableObservable<PayloadAtom> observable =
			Observable.<PayloadAtom>create(emitter -> {
				synchronized (TransactionAtoms.this) {
					checkConsumers(transactionAtom, emitter);
				}
				emitter.onComplete();
			}).replay();

		observable.connect();

		return new TransactionAtomsUpdate(observable);
	}
}
