package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.store.CommitmentBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class IterativeDiscoveryState implements TempoState {
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds

	private final Map<EUID, IterativeDiscoveryPeerState> states;

	public IterativeDiscoveryState(Map<EUID, IterativeDiscoveryPeerState> states) {
		this.states = states;
	}

	public IterativeCursorStage getStage(EUID nid) {
		IterativeDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.discoveryStage;
	}

	public boolean contains(EUID nid) {
		return states.containsKey(nid);
	}

	public boolean isPending(EUID nid, long requestedLCPosition) {
		IterativeDiscoveryPeerState state = states.get(nid);
		return state != null && state.isPending(requestedLCPosition);
	}

	public IterativeDiscoveryState with(EUID nid, CommitmentBatch initialCommitments) {
		Map<EUID, IterativeDiscoveryPeerState> nextStates = new HashMap<>(states);
		if (nextStates.containsKey(nid)) {
			throw new TempoException("State for '" + nid + "' already exists");
		}
		nextStates.put(nid, new IterativeDiscoveryPeerState(
			nid,
			ImmutableSet.of(),
			IterativeCursorStage.SYNCHRONISING,
			0,
			initialCommitments
		));
		return new IterativeDiscoveryState(nextStates);
	}

	public IterativeDiscoveryState withRequest(EUID nid, long requestedLCPosition) {
		Map<EUID, IterativeDiscoveryPeerState> nextStates = new HashMap<>(states);
		IterativeDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.with(requestedLCPosition));
		return new IterativeDiscoveryState(nextStates);
	}

	public IterativeDiscoveryState withoutRequest(EUID nid, long requestedLCPosition) {
		Map<EUID, IterativeDiscoveryPeerState> nextStates = new HashMap<>(states);
		IterativeDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.without(requestedLCPosition));
		return new IterativeDiscoveryState(nextStates);
	}

	public IterativeDiscoveryState withStage(EUID nid, IterativeCursorStage stage) {
		Map<EUID, IterativeDiscoveryPeerState> nextStates = new HashMap<>(states);
		IterativeDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.with(stage));
		return new IterativeDiscoveryState(nextStates);
	}

	public IterativeDiscoveryState without(EUID nid) {
		Map<EUID, IterativeDiscoveryPeerState> nextStates = new HashMap<>(this.states);
		nextStates.remove(nid);
		return new IterativeDiscoveryState(nextStates);
	}

	@Override
	public String toString() {
		return "IterativeDiscoveryState{" +
			"states=" + states + '}';
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"states", states
		);
	}

	public enum IterativeCursorStage {
		SYNCHRONISING,
		SYNCHRONISED;
	}

	public int getBackoff(EUID nid) {
		IterativeDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.backoffCounter;
	}

	public Stream<EUID> peers() {
		return this.states.keySet().stream();
	}

	public static IterativeDiscoveryState empty() {
		return new IterativeDiscoveryState(
			ImmutableMap.of()
		);
	}

	private static class IterativeDiscoveryPeerState {
		private final EUID nid;
		private final Set<Long> pendingDiscoveryRequests;
		private final IterativeCursorStage discoveryStage;
		private final int backoffCounter;
		private final CommitmentBatch recentCommitments;

		private IterativeDiscoveryPeerState(EUID nid, Set<Long> pendingDiscoveryRequests, IterativeCursorStage discoveryStage, int backoffCounter, CommitmentBatch recentCommitments) {
			this.nid = nid;
			this.pendingDiscoveryRequests = pendingDiscoveryRequests;
			this.discoveryStage = discoveryStage;
			this.backoffCounter = backoffCounter;
			this.recentCommitments = recentCommitments;
		}

		private IterativeDiscoveryPeerState with(CommitmentBatch commitments) {
			return new IterativeDiscoveryPeerState(
				nid,
				pendingDiscoveryRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments.pushLast(commitments)
			);
		}

		private IterativeDiscoveryPeerState with(long request) {
			Set<Long> nextPendingRequests = new HashSet<>(pendingDiscoveryRequests);
			nextPendingRequests.add(request);
			return new IterativeDiscoveryPeerState(
				nid,
				nextPendingRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments);
		}

		private IterativeDiscoveryPeerState without(long request) {
			Set<Long> nextPendingRequests = new HashSet<>(pendingDiscoveryRequests);
			nextPendingRequests.remove(request);
			return new IterativeDiscoveryPeerState(
				nid,
				nextPendingRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments);
		}

		private boolean isPending(long requestedLCPosition) {
			return pendingDiscoveryRequests.contains(requestedLCPosition);
		}

		private IterativeDiscoveryPeerState with(IterativeCursorStage nextStage) {
			int nextBackoffCounter;
			if (nextStage == IterativeCursorStage.SYNCHRONISED) {
				nextBackoffCounter = Math.min(MAX_BACKOFF, backoffCounter + 1);
			} else {
				nextBackoffCounter = 0;
			}
			return new IterativeDiscoveryPeerState(
				nid,
				pendingDiscoveryRequests,
				nextStage,
				nextBackoffCounter,
				recentCommitments);
		}
	}
}
