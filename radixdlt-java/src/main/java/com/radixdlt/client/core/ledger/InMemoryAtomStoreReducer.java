package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

/**
 * Wrapper reducer class for the Atom Store
 */
public final class InMemoryAtomStoreReducer {
	private final InMemoryAtomStore atomStore;

	public InMemoryAtomStoreReducer(InMemoryAtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public void reduce(RadixNodeAction action) {
		if (action instanceof FetchAtomsObservationAction) {
			FetchAtomsObservationAction fetchAtomsObservationAction = (FetchAtomsObservationAction) action;
			AtomObservation observation = fetchAtomsObservationAction.getObservation();
			this.atomStore.store(fetchAtomsObservationAction.getAddress(), observation);
		} else if (action instanceof SubmitAtomStatusAction) {

			// Soft storage of atoms so that atoms which are submitted and stored can
			// be immediately used instead of having to wait for fetch atom events.
			final SubmitAtomStatusAction submitAtomStatusAction = (SubmitAtomStatusAction) action;
			final Atom atom = submitAtomStatusAction.getAtom();

			if (submitAtomStatusAction.getStatusNotification().getAtomStatus() == AtomStatus.STORED) {
				atom.addresses().forEach(address -> {
					this.atomStore.store(address, AtomObservation.softStored(atom));
					this.atomStore.store(address, AtomObservation.head());
				});
			}
		}
	}
}
