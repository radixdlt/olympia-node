package com.radixdlt.client.application;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
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

	private RadixApplicationAPI(RadixIdentity identity, RadixLedger ledger) {
		this.identity = identity;
		this.ledger = ledger;
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		return new RadixApplicationAPI(identity, RadixUniverse.getInstance().getLedger());
	}

	public static RadixApplicationAPI create(RadixIdentity identity, RadixLedger ledger) {
		return new RadixApplicationAPI(identity, ledger);
	}

	public RadixIdentity getIdentity() {
		return identity;
	}

	public RadixAddress getAddress() {
		return ledger.getAddressFromPublicKey(identity.getPublicKey());
	}

	public Observable<EncryptedData> getEncryptedData(RadixAddress address) {
		return ledger.getAllAtoms(address.getUID(), ApplicationPayloadAtom.class)
			.filter(atom -> atom.getEncryptor() != null && atom.getEncryptor().getProtectors() != null)
			.map(EncryptedData::fromAtom);
	}

	public Observable<UnencryptedData> getDecryptableData(RadixAddress address) {
		return ledger.getAllAtoms(address.getUID(), ApplicationPayloadAtom.class)
			.flatMapMaybe(atom -> UnencryptedData.fromAtom(atom, identity));
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
