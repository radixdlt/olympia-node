package com.radixdlt.client.application;

import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.objects.EncryptedData;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;
import java.util.function.Supplier;

public class RadixApplicationAPI {
	public static class Result {
		private final Observable<AtomSubmissionUpdate> updates;
		private final Completable completable;

		private Result(Observable<AtomSubmissionUpdate> updates) {
			this.updates = updates;

			this.completable = updates.filter(AtomSubmissionUpdate::isComplete)
				.firstOrError()
				.flatMapCompletable(update -> {
					if (update.getState() == AtomSubmissionState.STORED) {
						return Completable.complete();
					} else {
						return Completable.error(new RuntimeException(update.getMessage()));
					}
				});
		}

		public Observable<AtomSubmissionUpdate> toObservable() {
			return updates;
		}

		public Completable toCompletable() {
			return completable;
		}
	}


	private final RadixIdentity identity;
	private final RadixLedger ledger;
	private final DataStoreTranslator dataStoreTranslator;
	private final Supplier<AtomBuilder> atomBuilderSupplier;

	private RadixApplicationAPI(RadixIdentity identity, RadixLedger ledger, Supplier<AtomBuilder> atomBuilderSupplier) {
		this.identity = identity;
		this.ledger = ledger;
		this.dataStoreTranslator = DataStoreTranslator.getInstance();
		this.atomBuilderSupplier = atomBuilderSupplier;
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		return new RadixApplicationAPI(identity, RadixUniverse.getInstance().getLedger(), AtomBuilder::new);
	}

	public static RadixApplicationAPI create(RadixIdentity identity, RadixLedger ledger, Supplier<AtomBuilder> atomBuilderSupplier) {
		return new RadixApplicationAPI(identity, ledger, atomBuilderSupplier);
	}

	public RadixIdentity getIdentity() {
		return identity;
	}

	public RadixAddress getAddress() {
		return ledger.getAddressFromPublicKey(identity.getPublicKey());
	}

	public Observable<UnencryptedData> getDecryptableData(RadixAddress address) {
		return ledger.getAllAtoms(address.getUID(), ApplicationPayloadAtom.class)
			.flatMapMaybe(atom -> UnencryptedData.fromAtom(atom, identity));
	}

	public Result storeData(EncryptedData encryptedData, RadixAddress address) {
		DataStore dataStore = new DataStore(encryptedData, address);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(() -> atomBuilder.buildWithPOWFee(ledger.getMagic(), address.getPublicKey())))
			.flatMap(identity::sign)
			.flatMapObservable(ledger::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	public Result storeData(EncryptedData encryptedData, RadixAddress address0, RadixAddress address1) {
		DataStore dataStore = new DataStore(encryptedData, address0, address1);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(() -> atomBuilder.buildWithPOWFee(ledger.getMagic(), address0.getPublicKey())))
			.flatMap(identity::sign)
			.flatMapObservable(ledger::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}
}
