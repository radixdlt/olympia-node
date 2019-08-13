package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AbandonIterativeSyncAction;
import com.radixdlt.tempo.actions.OnCursorSynchronisedAction;
import com.radixdlt.tempo.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.state.IterativeSyncState;
import com.radixdlt.tempo.state.IterativeSyncState.IterativeCursorStage;

import java.util.Set;

public class IterativeSyncReducer implements TempoReducer<IterativeSyncState> {
	@Override
	public Class<IterativeSyncState> stateClass() {
		return IterativeSyncState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public IterativeSyncState initialState() {
		return IterativeSyncState.empty();
	}

	@Override
	public IterativeSyncState reduce(IterativeSyncState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof OnCursorSynchronisedAction) {
			return prevState.withStage(((OnCursorSynchronisedAction) action).getPeerNid(), IterativeCursorStage.SYNCHRONISED);
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			EUID peerNid = request.getPeer().getSystem().getNID();
			long requestedLCPosition = request.getCursor().getLCPosition();
			IterativeSyncState nextState = prevState.withRequest(peerNid, requestedLCPosition);
			// if this is the next request after a response, we are actively synchronising
			if (request.isNext()) {
				nextState = nextState.withStage(peerNid, IterativeCursorStage.SYNCHRONISING);
			}
			return nextState;
		} else if (action instanceof ReceiveIterativeResponseAction) {
			ReceiveIterativeResponseAction response = (ReceiveIterativeResponseAction) action;
			EUID peerNid = response.getPeer().getSystem().getNID();
			long requestedLCPosition = response.getCursor().getLCPosition();
			return prevState.withoutRequest(peerNid, requestedLCPosition);
		} else if (action instanceof AbandonIterativeSyncAction) {
			return prevState.without(((AbandonIterativeSyncAction) action).getPeerNid());
		}

		return prevState;
	}
}
