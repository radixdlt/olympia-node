package com.radixdlt.tempo.discovery;

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
	private final Lock stateLock = new ReentrantLock(true);
	private final Map<EUID, IterativeDiscoveryPeerState> states = new HashMap<>();

	IterativeDiscoveryState() {
		// nothing to do here
	}

	boolean isDiscovered(EUID nid) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid) && states.get(nid).backoffCounter == 0);
	}

	boolean contains(EUID nid) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid));
	}

	boolean isPending(EUID nid, long request) {
		return Locking.withSupplierLock(stateLock, () -> states.containsKey(nid) && states.get(nid).isPending(request));
	}

	public void add(EUID nid) {
		Locking.withLock(stateLock, () -> states.computeIfAbsent(nid, n -> new IterativeDiscoveryPeerState()));
	}

	void remove(EUID nid) {
		Locking.withLock(stateLock, () -> states.remove(nid));
	}

	void addRequest(EUID nid, long request) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.addRequest(request);
		});
	}

	void removeRequest(EUID nid, long request) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.removeRequest(request);
		});
	}

	void onDiscovered(EUID nid) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.onDiscovered();
		});
	}

	void onDiscovering(EUID nid) {
		Locking.withLock(stateLock, () -> {
			IterativeDiscoveryPeerState state = states.get(nid);
			if (state == null) {
				throw new TempoException("State for nid '" + nid + "' does not exist");
			}
			state.onDiscovering();
		});
	}

	@Override
	public String toString() {
		return "IterativeDiscoveryState{" +
			"states=" + states + '}';
	}

	int getBackoff(EUID nid) {
		IterativeDiscoveryPeerState state = states.get(nid);
		if (state == null) {
			throw new TempoException("State for '" + nid + "' does not exist");
		}
		return state.backoffCounter;
	}

	public Stream<EUID> peers() {
		return this.states.keySet().stream();
	}

	public void reset() {
		Locking.withLock(stateLock, this.states::clear);
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
			this.backoffCounter = Math.min(backoffCounter + 1, Integer.MAX_VALUE - 1);
		}

		private void onDiscovering() {
			this.backoffCounter = 0;
		}
	}
}
