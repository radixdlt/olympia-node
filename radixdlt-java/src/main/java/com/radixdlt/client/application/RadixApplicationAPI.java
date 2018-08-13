package com.radixdlt.client.application;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Completable;
import io.reactivex.Observable;

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

		public Completable toCompletable() {
			return completable;
		}
	}

	private final RadixIdentity identity;
	private final RadixLedger ledger;

	public RadixApplicationAPI(RadixIdentity identity, RadixLedger ledger) {
		this.identity = identity;
		this.ledger = ledger;
	}

	public Result storeData(EncryptedData encryptedData, RadixAddress address) {
		StoreDataAction storeDataAction = new StoreDataAction(encryptedData, address);

		AtomBuilder atomBuilder = new AtomBuilder();
		storeDataAction.addToAtomBuilder(atomBuilder);
		UnsignedAtom unsignedAtom = atomBuilder.buildWithPOWFee(ledger.getMagic(), address.getPublicKey());

		return new Result(identity.sign(unsignedAtom).flatMapObservable(ledger::submitAtom));
	}

	public Result storeData(EncryptedData encryptedData, RadixAddress address0, RadixAddress address1) {
		StoreDataAction storeDataAction = new StoreDataAction(encryptedData, address0, address1);

		AtomBuilder atomBuilder = new AtomBuilder();
		storeDataAction.addToAtomBuilder(atomBuilder);
		UnsignedAtom unsignedAtom = atomBuilder.buildWithPOWFee(ledger.getMagic(), address0.getPublicKey());

		return new Result(identity.sign(unsignedAtom).flatMapObservable(ledger::submitAtom));
	}
}
