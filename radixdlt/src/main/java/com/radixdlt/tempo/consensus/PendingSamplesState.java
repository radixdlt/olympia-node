package com.radixdlt.tempo.consensus;

import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.TempoException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PendingSamplesState {
	private final Map<EUID, PendingSampling> pendingSamplings = new ConcurrentHashMap<>();

	void put(EUID tag, CompletableFuture<Samples> future, Set<LedgerIndex> requestedIndices, Collection<EUID> requestedPeers) {
		PendingSampling newSampling = new PendingSampling(future, requestedIndices, requestedPeers);
		if (pendingSamplings.putIfAbsent(tag, newSampling) != null) {
			throw new TempoException("Sampling with tag '" + tag + "' already exists");
		}
	}

	boolean contains(EUID tag) {
		return pendingSamplings.containsKey(tag);
	}

	boolean isPending(EUID tag) {
		return pendingSamplings.containsKey(tag);
	}

	boolean isPending(EUID tag, EUID peer) {
		PendingSampling state = pendingSamplings.get(tag);
		return state != null && state.isPending(peer);
	}

	Samples receiveSample(EUID tag, EUID peer, Sample sample) {
		PendingSampling sampling = pendingSamplings.get(tag);
		if (sampling == null) {
			throw new TempoException("Sampling with tag '" + tag + "' does not exist");
		}
		sampling.receiveSample(peer, sample);
		return sampling.attemptComplete();
	}

	Samples timeout(EUID tag) {
		PendingSampling sampling = pendingSamplings.get(tag);
		if (sampling == null) {
			throw new TempoException("Sampling with tag '" + tag + "' does not exist");
		}
		sampling.timeout();
		return sampling.attemptComplete();
	}

	public void remove(EUID tag) {
		this.pendingSamplings.remove(tag);
	}

	CompletableFuture<Samples> getFuture(EUID tag) {
		PendingSampling sampling = pendingSamplings.get(tag);
		if (sampling == null) {
			throw new TempoException("Sampling with tag '" + tag + "' does not exist");
		}
		return sampling.getFuture();
	}

	private static final class PendingSampling {
		private final CompletableFuture<Samples> future;
		private final Set<LedgerIndex> requestedIndices;
		private final Collection<EUID> requestedPeers;
		private final Set<EUID> pendingPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());
		private final Map<EUID, Sample> collectedSamples = new ConcurrentHashMap<>();

		private PendingSampling(CompletableFuture<Samples> future, Set<LedgerIndex> requestedIndices, Collection<EUID> requestedPeers) {
			this.future = future;
			this.requestedIndices = requestedIndices;
			this.requestedPeers = requestedPeers;
			this.pendingPeers.addAll(requestedPeers);
		}

		private boolean isPending(EUID peer) {
			return pendingPeers.contains(peer);
		}

		private boolean isComplete() {
			return pendingPeers.isEmpty();
		}

		void receiveSample(EUID peer, Sample sample) {
			if (!requestedPeers.contains(peer)) {
				throw new TempoException("Sample from peer '" + peer + "' was not requested in this sampling");
			}
			if (!pendingPeers.contains(peer)) {
				throw new TempoException("Sample from peer '" + peer + "' is not pending in this sampling");
			}

			this.collectedSamples.put(peer, sample);
		}

		void timeout() {
			this.pendingPeers.clear();
		}

		private CompletableFuture<Samples> getFuture() {
			return future;
		}

		private Set<LedgerIndex> getRequestedIndices() {
			return requestedIndices;
		}

		private Collection<EUID> getRequestedPeers() {
			return requestedPeers;
		}

		private Map<EUID, Sample> getCollectedSamples() {
			return collectedSamples;
		}

		private Samples attemptComplete() {
			if (this.isComplete()) {
				Set<EUID> unresponsivePeers = new HashSet<>(requestedPeers);
				unresponsivePeers.removeAll(collectedSamples.keySet());
				return new Samples(collectedSamples, unresponsivePeers);
			}
			return null;
		}
	}
}
