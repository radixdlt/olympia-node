package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.HandleFailedDeliveryAction;
import com.radixdlt.tempo.actions.messaging.ReceiveDeliveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendDeliveryRequestAction;
import com.radixdlt.tempo.state.DeliveryState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DeliveryReducer implements TempoReducer<DeliveryState> {
	@Override
	public Class<DeliveryState> stateClass() {
		return DeliveryState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public DeliveryState initialState() {
		return DeliveryState.empty();
	}

	@Override
	public DeliveryState reduce(DeliveryState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ReceiveDeliveryResponseAction) {
			Set<AID> nextState = new HashSet<>(prevState.getOngoingDeliveries());
			nextState.remove(((ReceiveDeliveryResponseAction) action).getAtom().getAID());
			return new DeliveryState(Collections.unmodifiableSet(nextState));
		} else if (action instanceof SendDeliveryRequestAction) {
			Set<AID> nextState = new HashSet<>(prevState.getOngoingDeliveries());
			nextState.addAll(((SendDeliveryRequestAction) action).getAids());
			return new DeliveryState(Collections.unmodifiableSet(nextState));
		} else if (action instanceof HandleFailedDeliveryAction) {
			Set<AID> nextState = new HashSet<>(prevState.getOngoingDeliveries());
			nextState.removeAll(((HandleFailedDeliveryAction) action).getAids());
			return new DeliveryState(Collections.unmodifiableSet(nextState));
		}

		return prevState;
	}
}
