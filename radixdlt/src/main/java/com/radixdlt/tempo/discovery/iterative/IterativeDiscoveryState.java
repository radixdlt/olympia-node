package com.radixdlt.tempo.discovery.iterative;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoException;
import org.radix.network2.utils.Locking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * The state of iterative discovery across multiple peers.
 * The individual peer states capture pending requests and a 'backoff' when discovery is synchronised.
 */
class IterativeDiscoveryState {
	private final int maxBackoff; // results in 2^4 -> 16 seconds

	private final Lock stateLock = new ReentrantLock(true);
	private final Map<EUID, IterativeDiscoveryPeerState> states = new HashMap<>();

	IterativeDiscoveryState(int maxBackoff) {
		this.maxBackoff = maxBackoff;
	}

	public boolean isDiscovered(EUID nid) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid) && states.get(nid).backoffCounter == 0);
	}

	public boolean contains(EUID nid) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid));
	}

	public boolean isPending(EUID nid, long request) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid) && states.get(nid).isPending(request));
	}

	public void add(EUID nid) {
		Locking.withLock(stateLock, () -> states.computeIfAbsent(nid, n -> new IterativeDiscoveryPeerState()));
	}

	public void remove(EUID nid) {
		Locking.withLock(stateLock, () -> states.remove(nid));
	}

	public void addRequest(EUID nid, long request) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.addRequest(request);
		});
	}

	public void removeRequest(EUID nid, long request) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.removeRequest(request);
		});
	}

	public void onDiscovered(EUID nid) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.onDiscovered();
		});
	}

	public void onDiscovering(EUID nid) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.onDiscovered();
		});
	}

	@Override
	public String toString() {
		return "IterativeDiscoveryState{" +
			"states=" + states + '}';
	}

	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"states", states
		);
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

	private class IterativeDiscoveryPeerState {
		private final Set<Long> pendingRequests;
		private int backoffCounter;

		private IterativeDiscoveryPeerState() {
			this.pendingRequests = new HashSet<>();
			this.backoffCounter = 0;
		}

		private void addRequest(long request) {
			pendingRequests.add(request);
		}

		private void removeRequest(long request) {
			pendingRequests.remove(request);
		}

		private boolean isPending(long requestedLCPosition) {
			return pendingRequests.contains(requestedLCPosition);
		}

		private void onDiscovered() {
			this.backoffCounter = Math.min(maxBackoff, backoffCounter + 1);
		}

		private void onDiscovering() {
			this.backoffCounter = 0;
		}
	}
}
