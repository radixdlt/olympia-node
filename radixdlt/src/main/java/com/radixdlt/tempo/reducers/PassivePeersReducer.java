package com.radixdlt.tempo.reducers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoReducer;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.state.LivePeersState;
import com.radixdlt.tempo.state.PassivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.Map;
import java.util.Set;

public class PassivePeersReducer implements TempoReducer<PassivePeersState> {
	private static final Logger logger = Logging.getLogger("Tempo");

	private final int desiredPeersCount;

	public PassivePeersReducer(int desiredPeersCount) {
		this.desiredPeersCount = desiredPeersCount;
	}

	@Override
	public Class<PassivePeersState> stateClass() {
		return PassivePeersState.class;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(LivePeersState.class);
	}

	@Override
	public PassivePeersState initialState() {
		return new PassivePeersState(ImmutableMap.of());
	}

	@Override
	public PassivePeersState reduce(PassivePeersState prevState, TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ReselectPassivePeersAction) {
			LivePeersState livePeers = bundle.get(LivePeersState.class);
			ImmutableMap<EUID, Peer> nextPassivePeers = livePeers.getLivePeers().entrySet().stream()
				.limit(desiredPeersCount)
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
			if (!Sets.difference(prevState.getSelectedPeers().keySet(), nextPassivePeers.keySet()).isEmpty()) {
				logger.info("Selected " + nextPassivePeers.size() + " passive peers: " + nextPassivePeers);
			}

			return new PassivePeersState(nextPassivePeers);
		}

		return prevState;
	}
}
