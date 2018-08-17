package com.radixdlt.client.dapps.wallet;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.observables.GroupedObservable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class RadixWallet {

	/**
	 * Lock to protect default wallet instance
	 */
	private static Object lock = new Object();
	private static RadixWallet radixWallet;

	public static RadixWallet getInstance() {
		synchronized (lock) {
			if (radixWallet == null) {
				radixWallet = new RadixWallet(RadixUniverse.getInstance());
			}
			return radixWallet;
		}
	}

	private final RadixLedger ledger;
	private final RadixUniverse universe;
	private final ConsumableDataSource consumableDataSource;

	RadixWallet(RadixUniverse universe) {
		this.universe = universe;
		this.ledger = universe.getLedger();
		this.consumableDataSource = new ConsumableDataSource(ledger);
	}

	public Observable<Long> getSubUnitBalance(RadixAddress address, EUID assetId) {
		return this.consumableDataSource.getConsumables(address)
			.map(Collection::stream)
			.map(stream -> stream
				.filter(consumable -> consumable.getAssetId().equals(assetId))
				.mapToLong(Consumable::getQuantity)
				.sum()
			)
			.share();
	}

	public Observable<Long> getSubUnitBalance(RadixAddress address, Asset asset) {
		return this.getSubUnitBalance(address, asset.getId());
	}

	public Observable<Long> getXRDSubUnitBalance(RadixAddress address) {
		return this.getSubUnitBalance(address, Asset.XRD.getId());
	}

	public Observable<WalletTransaction> getXRDTransactions(RadixAddress address) {
		return
			Observable.combineLatest(
				Observable.fromCallable(() -> new TransactionAtoms(address, Asset.XRD.getId())),
				ledger.getAllAtoms(address.getUID(), TransactionAtom.class),
				(transactionAtoms, atom) ->
					transactionAtoms.accept(atom)
						.getNewValidTransactions()
			)
			.flatMap(atom -> atom)
			.map(atom -> new WalletTransaction(address, atom));
	}

	public Observable<GroupedObservable<Set<ECPublicKey>, WalletTransaction>> getXRDTransactionsGroupedByParticipants(RadixAddress address) {
		return this.getXRDTransactions(address)
			.groupBy(transaction ->
				transaction.getTransactionAtom()
					.getParticles().stream()
					.map(Particle::getOwnersPublicKeys)
					.flatMap(Set::stream)
					.filter(publicKey -> !address.getPublicKey().equals(publicKey))
					.distinct()
					.collect(Collectors.toSet())
			);
	}

	/**
	 * Creates a new transaction atom from available consumables on subscription.
	 * Mainly for internal use. Use transferXRD and transferXRDWhenAvailable for
	 * the simplest interface.
	 *
	 * @param amountInSubUnits amount to transfer
	 * @param fromAddress address which consumables will originate from
	 * @param toAddress address to send XRD to
	 * @param payload message or data one can attach to the atom
	 * @param withPOWFee whether or not to calculate and add a POW fee consumable
	 * @return a new unsigned atom to be created on subscription
	 */
	public Single<UnsignedAtom> createXRDTransaction(
		long amountInSubUnits,
		RadixAddress fromAddress,
		RadixAddress toAddress,
		byte[] payload,
		boolean withPOWFee,
		Particle extraParticle
	) {
		return this.consumableDataSource.getConsumables(fromAddress)
			.firstOrError()
			.map(unconsumedConsumables -> {
				AtomBuilder atomBuilder = new AtomBuilder();
				atomBuilder.type(TransactionAtom.class);

				if (payload != null) {
					atomBuilder.payload(payload);
				}

				long consumerTotal = 0;
				Iterator<Consumable> iterator = unconsumedConsumables.iterator();
				Map<Set<ECKeyPair>, Long> consumerQuantities = new HashMap<>();

				if (extraParticle != null) {
					atomBuilder.addParticle(extraParticle);
				}

				// HACK for now
				// TODO: remove this, create a ConsumersCreator
				// TODO: randomize this to decrease probability of collision
				while (consumerTotal < amountInSubUnits && iterator.hasNext()) {
					final long left = amountInSubUnits - consumerTotal;

					com.radixdlt.client.core.atoms.Consumer newConsumer = iterator.next().toConsumer();
					consumerTotal += newConsumer.getQuantity();

					final long amount = Math.min(left, newConsumer.getQuantity());
					newConsumer.addConsumerQuantities(amount, Collections.singleton(toAddress.toECKeyPair()),
						consumerQuantities);

					atomBuilder.addParticle(newConsumer);
				}

				if (consumerTotal < amountInSubUnits) {
					Exceptions.propagate(new InsufficientFundsException(Asset.XRD, consumerTotal, amountInSubUnits));
				}

				List<Consumable> consumables = consumerQuantities.entrySet().stream()
					.map(entry -> new Consumable(entry.getValue(), entry.getKey(), System.nanoTime(), Asset.XRD.getId())).collect(
						Collectors.toList());
				atomBuilder.addParticles(consumables);

				if (withPOWFee) {
					// TODO: Replace this with public key of processing node runner
					return atomBuilder.buildWithPOWFee(ledger.getMagic(), fromAddress.getPublicKey());
				} else {
					return atomBuilder.build();
				}
			});
	}

	public Observable<AtomSubmissionUpdate> transferXRD(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		Particle extraParticle
	) {
		return this.transferXRD(amountInSubUnits, fromIdentity, toAddress, null, extraParticle);
	}

	public Observable<AtomSubmissionUpdate> transferXRD(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress
	) {
		return this.transferXRD(amountInSubUnits, fromIdentity, toAddress, (byte[]) null);
	}

	public Observable<AtomSubmissionUpdate> transferXRD(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		String payload
	) {
		return this.transferXRD(amountInSubUnits, fromIdentity, toAddress, payload.getBytes());
	}

	public Observable<AtomSubmissionUpdate> transferXRD(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		byte[] payload
	) {
		return this.transferXRD(amountInSubUnits, fromIdentity, toAddress, payload, null);
	}

	public Observable<AtomSubmissionUpdate> transferXRD(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		byte[] payload,
		Particle extraParticle
	) {
		if (amountInSubUnits <= 0) {
			throw new IllegalArgumentException("Cannot send negative or 0 XRD.");
		}
		Objects.requireNonNull(fromIdentity);
		Objects.requireNonNull(toAddress);

		RadixAddress fromAddress = universe.getAddressFrom(fromIdentity.getPublicKey());
		ConnectableObservable<AtomSubmissionUpdate> statusObservable =
			this.createXRDTransaction(amountInSubUnits, fromAddress, toAddress, payload, true, extraParticle)
				.flatMap(fromIdentity::sign)
				.flatMapObservable(ledger::submitAtom)
				.replay();
		statusObservable.connect();
		return statusObservable;
	}

	public Observable<AtomSubmissionUpdate> transferXRDWhenAvailable(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		Particle extraParticle
	) {
		return this.transferXRDWhenAvailable(amountInSubUnits, fromIdentity, toAddress, null, extraParticle);
	}


	public Observable<AtomSubmissionUpdate> transferXRDWhenAvailable(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress
	) {
		return this.transferXRDWhenAvailable(amountInSubUnits, fromIdentity, toAddress, (byte[]) null);
	}

	public Observable<AtomSubmissionUpdate> transferXRDWhenAvailable(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		String payload
	) {
		return this.transferXRDWhenAvailable(amountInSubUnits, fromIdentity, toAddress, payload.getBytes());
	}

	public Observable<AtomSubmissionUpdate> transferXRDWhenAvailable(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		byte[] payload
	) {
		return this.transferXRDWhenAvailable(amountInSubUnits, fromIdentity, toAddress, payload, null);
	}

	public Observable<AtomSubmissionUpdate> transferXRDWhenAvailable(
		long amountInSubUnits,
		RadixIdentity fromIdentity,
		RadixAddress toAddress,
		byte[] payload,
		Particle extraParticle
	) {
		if (amountInSubUnits <= 0) {
			throw new IllegalArgumentException("Cannot send negative or 0 XRD.");
		}

		RadixAddress fromAddress = universe.getAddressFrom(fromIdentity.getPublicKey());
		ConnectableObservable<AtomSubmissionUpdate> status = this.getXRDSubUnitBalance(fromAddress)
			.filter(balance -> balance > amountInSubUnits)
			.firstOrError()
			.ignoreElement()
			.andThen(
				Single.fromCallable(
					() -> this.transferXRD(amountInSubUnits, fromIdentity, toAddress, payload, extraParticle)
				).flatMapObservable(t -> t))
			.replay();

		status.connect();
		return status;
	}
}
