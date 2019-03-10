package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.ledger.AtomStore;
import io.reactivex.Observable;

public class ActionStore<T> {
	private final AtomStore atomStore;
	private final AtomToExecutedActionsMapper<T> actionMapper;

	public ActionStore(
		AtomStore atomStore,
		AtomToExecutedActionsMapper<T> actionMapper
	) {
		this.atomStore = atomStore;
		this.actionMapper = actionMapper;
	}

	public Observable<T> getActions(RadixAddress address, RadixIdentity identity) {
		return atomStore.getAtoms(address)
			.filter(AtomObservation::isStore)
			.flatMap(a -> actionMapper.map(a.getAtom(), identity));
	}
}
