package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class IterativeSyncState implements TempoState {
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds

	private final Map<EUID, Set<Long>> pendingRequests;
	private final Map<EUID, IterativeCursorStage> currentStage;
	private final Map<EUID, Integer> backoffCounter;

	public IterativeSyncState(Map<EUID, Set<Long>> pendingRequests,
	                          Map<EUID, IterativeCursorStage> currentStage,
	                          Map<EUID, Integer> backoffCounter) {
		this.pendingRequests = pendingRequests;
		this.currentStage = currentStage;
		this.backoffCounter = backoffCounter;
	}

	public IterativeCursorStage getStage(EUID nid) {
		return currentStage.get(nid);
	}

	public boolean contains(EUID nid) {
		return currentStage.containsKey(nid);
	}

	public boolean isPending(EUID nid, long requestedLCPosition) {
		Set<Long> pendingForNid = pendingRequests.get(nid);
		if (pendingForNid == null) {
			return false;
		}
		return pendingForNid.contains(requestedLCPosition);
	}

	public IterativeSyncState withRequest(EUID nid, long requestedLCPosition) {
		Map<EUID, Set<Long>> nextPendingRequests = new HashMap<>(pendingRequests);
		nextPendingRequests.compute(nid, (n, p) -> {
			if (p == null) {
				p = new HashSet<>();
			}
			p.add(requestedLCPosition);
			return p;
		});
		return new IterativeSyncState(
			Collections.unmodifiableMap(nextPendingRequests),
			this.currentStage,
			this.backoffCounter
		);
	}

	public IterativeSyncState withoutRequest(EUID nid, long requestedLCPosition) {
		Map<EUID, Set<Long>> nextPendingRequests = new HashMap<>(pendingRequests);
		nextPendingRequests.computeIfPresent(nid, (n, p) -> {
			p.remove(requestedLCPosition);
			return p;
		});
		return new IterativeSyncState(
			Collections.unmodifiableMap(nextPendingRequests),
			this.currentStage,
			this.backoffCounter
		);
	}

	public IterativeSyncState withStage(EUID nid, IterativeCursorStage stage) {
		Map<EUID, IterativeCursorStage> nextStages = new HashMap<>(this.currentStage);
		nextStages.put(nid, stage);
		Map<EUID, Integer> nextBackoffCounter = new HashMap<>(this.backoffCounter);
		if (stage == IterativeCursorStage.SYNCHRONISED) {
			nextBackoffCounter.compute(nid, (n, c) -> c == null ? 0 : Math.min(MAX_BACKOFF, c + 1));
		} else {
			nextBackoffCounter.put(nid, 0);
		}
		return new IterativeSyncState(
			this.pendingRequests,
			Collections.unmodifiableMap(nextStages),
			Collections.unmodifiableMap(nextBackoffCounter)
		);
	}

	@Override
	public String toString() {
		return "IterativeSyncState{" +
			"pendingRequests=" + pendingRequests +
			", currentStage=" + currentStage +
			", backoffCounter=" + backoffCounter +
			'}';
	}

	public IterativeSyncState without(EUID nid) {
		Map<EUID, Set<Long>> nextPendingRequests = new HashMap<>(this.pendingRequests);
		Map<EUID, IterativeCursorStage> nextStages = new HashMap<>(this.currentStage);
		Map<EUID, Integer> nextBackoffCounter = new HashMap<>(this.backoffCounter);
		nextPendingRequests.remove(nid);
		nextStages.remove(nid);
		nextBackoffCounter.remove(nid);
		return new IterativeSyncState(
			nextPendingRequests,
			nextStages,
			nextBackoffCounter
		);
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"pendingRequests", pendingRequests,
			"currentStage", currentStage,
			"backoffCounter", backoffCounter
		);
	}

	public enum IterativeCursorStage {
		SYNCHRONISING,
		SYNCHRONISED;
	}
	public int getBackoff(EUID nid) {
		Integer backoff = backoffCounter.get(nid);
		if (backoff == null) {
			return 0;
		} else {
			return backoff;
		}
	}

	public Stream<EUID> peers() {
		return this.currentStage.keySet().stream();
	}

	public static IterativeSyncState empty() {
		return new IterativeSyncState(
			ImmutableMap.of(),
			ImmutableMap.of(),
			ImmutableMap.of()
		);
	}
}
