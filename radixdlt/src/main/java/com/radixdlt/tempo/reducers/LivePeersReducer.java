package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.RefreshLivePeersAction;
import com.radixdlt.tempo.peers.PeerSupplier;
import com.radixdlt.tempo.state.LivePeersState;

import java.util.Set;

// FIXME awful hack to get the old peer supplier interface mapped to a TempoState
//       should really be all reactive driven
public class LivePeersReducer implements TempoReducer<LivePeersState> {
	private final PeerSupplier peerSupplier;

	public LivePeersReducer(PeerSupplier peerSupplier) {
		this.peerSupplier = peerSupplier;
	}

	@Override
	public Class<LivePeersState> stateClass() {
		return LivePeersState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	@Override
	public LivePeersState initialState() {
		return LivePeersState.empty();
	}

	@Override
	public LivePeersState reduce(LivePeersState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof RefreshLivePeersAction) {
			return new LivePeersState(peerSupplier.getPeers().stream()
				.collect(ImmutableMap.toImmutableMap(p -> p.getSystem().getNID(), p -> p)));
		}

		return prevState;
	}
}
