package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.reactive.TempoState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PositionDiscoveryState implements TempoState {
	private Map<EUID, PositionDiscoveryPeerState> states;

	private PositionDiscoveryState(Map<EUID, PositionDiscoveryPeerState> states) {
		this.states = states;
	}

	public static PositionDiscoveryState empty() {
		return new PositionDiscoveryState(ImmutableMap.of());
	}

	public boolean isPending(EUID nid, long positionRequest) {
		PositionDiscoveryPeerState state = states.get(nid);
		return state != null && state.isPending(positionRequest);
	}

	public boolean isPending(EUID nid, Set<Long> positionRequests) {
		PositionDiscoveryPeerState state = states.get(nid);
		return state != null && state.isPending(positionRequests);
	}

	public PositionDiscoveryState with(EUID nid, Set<Long> positionRequests) {
		Map<EUID, PositionDiscoveryPeerState> nextStates = new HashMap<>(states);
		PositionDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			state = new PositionDiscoveryPeerState(positionRequests);
		} else {
			state = state.with(positionRequests);
		}
		nextStates.put(nid, state);
		return new PositionDiscoveryState(Collections.unmodifiableMap(nextStates));
	}

	public PositionDiscoveryState without(EUID nid, Set<Long> positionRequests) {
		Map<EUID, PositionDiscoveryPeerState> nextStates = new HashMap<>(states);
		PositionDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for node '" + nid + "' does not exist");
		}
		nextStates.put(nid, state.without(positionRequests));
		return new PositionDiscoveryState(Collections.unmodifiableMap(nextStates));
	}

	public Set<Long> getPending(EUID nid) {
		PositionDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for node '" + nid + "' does not exist");
		}
		return state.pendingPositionRequests;
	}

	private final class PositionDiscoveryPeerState {
		private final Set<Long> pendingPositionRequests;

		private PositionDiscoveryPeerState(Set<Long> pendingPositionRequests) {
			this.pendingPositionRequests = pendingPositionRequests;
		}

		private boolean isPending(long positionRequest) {
			return pendingPositionRequests.contains(positionRequest);
		}

		private PositionDiscoveryPeerState with(Set<Long> positionRequests) {
			Set<Long> nextPositionRequests = new HashSet<>(pendingPositionRequests);
			nextPositionRequests.addAll(positionRequests);
			return new PositionDiscoveryPeerState(Collections.unmodifiableSet(nextPositionRequests));
		}

		private PositionDiscoveryPeerState without(Set<Long> positionRequests) {
			Set<Long> nextPositionRequests = new HashSet<>(pendingPositionRequests);
			nextPositionRequests.removeAll(positionRequests);
			return new PositionDiscoveryPeerState(Collections.unmodifiableSet(nextPositionRequests));
		}

		public boolean isPending(Set<Long> positionRequests) {
			for (Long positionRequest : positionRequests) {
				if (pendingPositionRequests.contains(positionRequest)) {
					return true;
				}
			}
			return false;
		}
	}
}
