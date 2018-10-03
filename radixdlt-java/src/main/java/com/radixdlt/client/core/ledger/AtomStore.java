package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;

public interface AtomStore {
	Observable<AtomObservation> getAtoms(EUID destination);
}
