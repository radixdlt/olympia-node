package com.radixdlt.tempo.reducers;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoReducer;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.messaging.ReceiveCursorDiscoveryResponseAction;
import com.radixdlt.tempo.state.IterativeDiscoveryState;
import com.radixdlt.tempo.state.IterativeDiscoveryState.IterativeDiscoveryStage;

public class IterativeDiscoveryReducer implements TempoReducer<IterativeDiscoveryState> {
	@Override
	public Class<IterativeDiscoveryState> stateClass() {
		return IterativeDiscoveryState.class;
	}

	@Override
	public IterativeDiscoveryState initialState() {
		return IterativeDiscoveryState.empty();
	}

	@Override
	public IterativeDiscoveryState reduce(IterativeDiscoveryState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof InitiateIterativeDiscoveryAction) {
			InitiateIterativeDiscoveryAction initialDiscovery = (InitiateIterativeDiscoveryAction) action;
			EUID peerNid = initialDiscovery.getPeer().getSystem().getNID();
			return prevState.with(peerNid);
		} else if (action instanceof OnDiscoveryCursorSynchronisedAction) {
			return prevState.withStage(((OnDiscoveryCursorSynchronisedAction) action).getPeerNid(), IterativeDiscoveryStage.SYNCHRONISED);
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			long requestedLCPosition = request.getCursor().getLcPosition();
			IterativeDiscoveryState nextState = prevState.withRequest(peerNid, requestedLCPosition);
			// if this is the next request after a response, we are actively synchronising
			if (request.isNext()) {
				nextState = nextState.withStage(peerNid, IterativeDiscoveryStage.BEHIND);
			}
			return nextState;
		} else if (action instanceof ReceiveCursorDiscoveryResponseAction) {
			ReceiveCursorDiscoveryResponseAction response = (ReceiveCursorDiscoveryResponseAction) action;
			EUID peerNid = response.getPeer().getSystem().getNID();
			long requestedLCPosition = response.getCursor().getLcPosition();
			return prevState.completeRequest(peerNid, requestedLCPosition, response.getCommitments());
		} else if (action instanceof AbandonIterativeDiscoveryAction) {
			return prevState.without(((AbandonIterativeDiscoveryAction) action).getPeerNid());
		}

		return prevState;
	}
}
