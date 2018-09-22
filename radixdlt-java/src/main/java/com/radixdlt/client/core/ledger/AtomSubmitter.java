package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Observable;

public interface AtomSubmitter {
	Observable<AtomSubmissionUpdate> submitAtom(Atom atom);
}
