package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnAtomDeliveryFailedAction;
import com.radixdlt.tempo.actions.messaging.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendDeliveryRequestAction;
import com.radixdlt.tempo.state.AtomDeliveryState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AtomDeliveryReducer implements TempoReducer<AtomDeliveryState> {
	@Override
	public Class<AtomDeliveryState> stateClass() {
		return AtomDeliveryState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public AtomDeliveryState initialState() {
		return AtomDeliveryState.empty();
	}

	@Override
	public AtomDeliveryState reduce(AtomDeliveryState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ReceiveDeliveryResponseAction) {
			Map<AID, EUID> nextState = new HashMap<>(prevState.getPendingDeliveries());
			nextState.remove(((ReceiveDeliveryResponseAction) action).getAtom().getAID());
			return new AtomDeliveryState(Collections.unmodifiableMap(nextState));
		} else if (action instanceof SendDeliveryRequestAction) {
			Map<AID, EUID> nextState = new HashMap<>(prevState.getPendingDeliveries());
			SendDeliveryRequestAction request = (SendDeliveryRequestAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			request.getAids().forEach(aid -> nextState.put(aid, peerNid));
			return new AtomDeliveryState(Collections.unmodifiableMap(nextState));
		} else if (action instanceof OnAtomDeliveryFailedAction) {
			Map<AID, EUID> nextState = new HashMap<>(prevState.getPendingDeliveries());
			((OnAtomDeliveryFailedAction) action).getAids().forEach(nextState::remove);
			return new AtomDeliveryState(Collections.unmodifiableMap(nextState));
		}

		return prevState;
	}
}
