package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.Observable;

public interface AtomStore {
	Observable<Atom> getAtoms(EUID destination);
}
