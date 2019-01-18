package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO introduce proper abstractions for ValidAtomFilter, see RLAU-508
public class ValidAtomFilter {
	public ValidAtomFilter() {
	}

	public Observable<AtomObservation> filter(AtomObservation atomObservation) {
		return Observable.just(atomObservation);
	}
}
