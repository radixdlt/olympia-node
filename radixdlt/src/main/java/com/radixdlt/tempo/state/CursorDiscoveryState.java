package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.reactive.TempoState;
import com.radixdlt.tempo.store.CommitmentBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class CursorDiscoveryState implements TempoState {
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds

	private final Map<EUID, CursorDiscoveryPeerState> states;

	public CursorDiscoveryState(Map<EUID, CursorDiscoveryPeerState> states) {
		this.states = states;
	}

	public IterativeCursorStage getStage(EUID nid) {
		CursorDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.discoveryStage;
	}

	public boolean contains(EUID nid) {
		return states.containsKey(nid);
	}

	public boolean isPending(EUID nid, long requestedLCPosition) {
		CursorDiscoveryPeerState state = states.get(nid);
		return state != null && state.isPending(requestedLCPosition);
	}

	public CommitmentBatch getRecentCommitments(EUID nid) {
		CursorDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.recentCommitments;
	}

	public CursorDiscoveryState with(EUID nid, CommitmentBatch initialCommitments) {
		Map<EUID, CursorDiscoveryPeerState> nextStates = new HashMap<>(states);
		if (nextStates.containsKey(nid)) {
			throw new TempoException("State for '" + nid + "' already exists");
		}
		nextStates.put(nid, new CursorDiscoveryPeerState(
			ImmutableSet.of(),
			IterativeCursorStage.BEHIND,
			0,
			initialCommitments
		));
		return new CursorDiscoveryState(nextStates);
	}

	public CursorDiscoveryState withRequest(EUID nid, long requestedLCPosition) {
		Map<EUID, CursorDiscoveryPeerState> nextStates = new HashMap<>(states);
		CursorDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.with(requestedLCPosition));
		return new CursorDiscoveryState(nextStates);
	}

	public CursorDiscoveryState completeRequest(EUID nid, long requestedLCPosition, CommitmentBatch commitmentBatch) {
		Map<EUID, CursorDiscoveryPeerState> nextStates = new HashMap<>(states);
		CursorDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.complete(requestedLCPosition, commitmentBatch));
		return new CursorDiscoveryState(nextStates);
	}

	public CursorDiscoveryState withStage(EUID nid, IterativeCursorStage stage) {
		Map<EUID, CursorDiscoveryPeerState> nextStates = new HashMap<>(states);
		CursorDiscoveryPeerState prevState = states.get(nid);
		if (prevState == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		nextStates.put(nid, prevState.with(stage));
		return new CursorDiscoveryState(nextStates);
	}

	public CursorDiscoveryState without(EUID nid) {
		Map<EUID, CursorDiscoveryPeerState> nextStates = new HashMap<>(this.states);
		nextStates.remove(nid);
		return new CursorDiscoveryState(nextStates);
	}

	@Override
	public String toString() {
		return "CursorDiscoveryState{" +
			"states=" + states + '}';
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"states", states
		);
	}

	public enum IterativeCursorStage {
		BEHIND,
		SYNCHRONISED
	}

	public int getBackoff(EUID nid) {
		CursorDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.backoffCounter;
	}

	public Stream<EUID> peers() {
		return this.states.keySet().stream();
	}

	public static CursorDiscoveryState empty() {
		return new CursorDiscoveryState(
			ImmutableMap.of()
		);
	}

	private static class CursorDiscoveryPeerState {
		private final Set<Long> pendingDiscoveryRequests;
		private final IterativeCursorStage discoveryStage;
		private final int backoffCounter;
		private final CommitmentBatch recentCommitments;

		private CursorDiscoveryPeerState(Set<Long> pendingDiscoveryRequests, IterativeCursorStage discoveryStage, int backoffCounter, CommitmentBatch recentCommitments) {
			this.pendingDiscoveryRequests = pendingDiscoveryRequests;
			this.discoveryStage = discoveryStage;
			this.backoffCounter = backoffCounter;
			this.recentCommitments = recentCommitments;
		}

		private CursorDiscoveryPeerState with(CommitmentBatch commitments) {
			return new CursorDiscoveryPeerState(
				pendingDiscoveryRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments.pushLast(commitments)
			);
		}

		private CursorDiscoveryPeerState with(long request) {
			Set<Long> nextPendingRequests = new HashSet<>(pendingDiscoveryRequests);
			nextPendingRequests.add(request);
			return new CursorDiscoveryPeerState(
				nextPendingRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments
			);
		}

		private CursorDiscoveryPeerState complete(long request, CommitmentBatch commitmentBatch) {
			Set<Long> nextPendingRequests = new HashSet<>(pendingDiscoveryRequests);
			nextPendingRequests.remove(request);
			return new CursorDiscoveryPeerState(
				nextPendingRequests,
				discoveryStage,
				backoffCounter,
				recentCommitments.pushLast(commitmentBatch)
			);
		}

		private boolean isPending(long requestedLCPosition) {
			return pendingDiscoveryRequests.contains(requestedLCPosition);
		}

		private CursorDiscoveryPeerState with(IterativeCursorStage nextStage) {
			int nextBackoffCounter;
			if (nextStage == IterativeCursorStage.SYNCHRONISED) {
				nextBackoffCounter = Math.min(MAX_BACKOFF, backoffCounter + 1);
			} else {
				nextBackoffCounter = 0;
			}
			return new CursorDiscoveryPeerState(
				pendingDiscoveryRequests,
				nextStage,
				nextBackoffCounter,
				recentCommitments
			);
		}
	}
}
