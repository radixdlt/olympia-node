package com.radixdlt.tempo.reducers;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.messaging.ReceivePositionDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendPositionDiscoveryRequestAction;
import com.radixdlt.tempo.state.PositionDiscoveryState;

public class PositionDiscoveryReducer implements TempoReducer<PositionDiscoveryState> {
	@Override
	public Class<PositionDiscoveryState> stateClass() {
		return PositionDiscoveryState.class;
	}

	@Override
	public PositionDiscoveryState initialState() {
		return PositionDiscoveryState.empty();
	}

	@Override
	public PositionDiscoveryState reduce(PositionDiscoveryState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof SendPositionDiscoveryRequestAction) {
			SendPositionDiscoveryRequestAction request = (SendPositionDiscoveryRequestAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			return prevState.with(peerNid, request.getPositions());
		} else if (action instanceof ReceivePositionDiscoveryRequestAction) {
			ReceivePositionDiscoveryRequestAction request = (ReceivePositionDiscoveryRequestAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			return prevState.with(peerNid, request.getPositions());
		}

		return prevState;
	}
}
