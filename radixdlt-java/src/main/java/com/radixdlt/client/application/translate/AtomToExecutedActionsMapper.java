package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.Observable;

public interface AtomToExecutedActionsMapper<T> {
	Class<T> actionClass();
	Observable<T> map(Atom a, RadixIdentity identity);
}
