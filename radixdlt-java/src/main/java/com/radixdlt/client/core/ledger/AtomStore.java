package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;

public interface AtomStore {
	Observable<AtomObservation> getAtoms(RadixAddress address);
}
