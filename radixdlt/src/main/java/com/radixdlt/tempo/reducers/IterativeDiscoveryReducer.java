package com.radixdlt.tempo.reducers;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.state.IterativeDiscoveryState;
import com.radixdlt.tempo.state.IterativeDiscoveryState.IterativeCursorStage;
import com.radixdlt.tempo.store.CommitmentBatch;

import java.util.Optional;

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
			Optional<CommitmentBatch> initialCommitments = initialDiscovery.getInitialCommitments();
			if (initialCommitments.isPresent()) {
				EUID peerNid = initialDiscovery.getPeer().getSystem().getNID();
				return prevState.with(peerNid, initialCommitments.get());
			}
		} else if (action instanceof OnDiscoveryCursorSynchronisedAction) {
			return prevState.withStage(((OnDiscoveryCursorSynchronisedAction) action).getPeerNid(), IterativeCursorStage.SYNCHRONISED);
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			long requestedLCPosition = request.getCursor().getLcPosition();
			IterativeDiscoveryState nextState = prevState.withRequest(peerNid, requestedLCPosition);
			// if this is the next request after a response, we are actively synchronising
			if (request.isNext()) {
				nextState = nextState.withStage(peerNid, IterativeCursorStage.SYNCHRONISING);
			}
			return nextState;
		} else if (action instanceof ReceiveIterativeDiscoveryResponseAction) {
			ReceiveIterativeDiscoveryResponseAction response = (ReceiveIterativeDiscoveryResponseAction) action;
			EUID peerNid = response.getPeer().getSystem().getNID();
			long requestedLCPosition = response.getCursor().getLcPosition();
			return prevState.withoutRequest(peerNid, requestedLCPosition);
		} else if (action instanceof AbandonIterativeDiscoveryAction) {
			return prevState.without(((AbandonIterativeDiscoveryAction) action).getPeerNid());
		}

		return prevState;
	}
}
