package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoReducer;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.RefreshLivePeersAction;
import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

// FIXME awful hack to get the old peer supplier interface mapped to a TempoState
//       should really be all reactive driven
public class LivePeersReducer implements TempoReducer<LivePeersState> {
	private static final Logger logger = Logging.getLogger("Tempo");
	private final PeerSupplier peerSupplier;

	public LivePeersReducer(PeerSupplier peerSupplier) {
		this.peerSupplier = peerSupplier;
	}

	@Override
	public Class<LivePeersState> stateClass() {
		return LivePeersState.class;
	}

	@Override
	public LivePeersState initialState() {
		return LivePeersState.empty();
	}

	@Override
	public LivePeersState reduce(LivePeersState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof RefreshLivePeersAction) {
			LivePeersState livePeersState = new LivePeersState(peerSupplier.getPeers().stream()
				.collect(ImmutableMap.toImmutableMap(p -> p.getSystem().getNID(), p -> p)));
			if (logger.hasLevel(Logging.TRACE)) {
				logger.trace("Detected " + livePeersState.getLivePeers().size() + " live peers");
			}
			return livePeersState;
		}

		return prevState;
	}
}
