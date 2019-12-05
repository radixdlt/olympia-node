package com.radixdlt.delivery;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.network2.addressbook.Peer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class PendingDeliveryState {
	private final Map<AID, PendingDelivery> pendingDeliveries;

	PendingDeliveryState() {
		this.pendingDeliveries = new ConcurrentHashMap<>();
	}

	Peer popFallback(AID aid) {
		PendingDelivery pendingDelivery = pendingDeliveries.get(aid);
		if (pendingDelivery == null) {
			return null;
		}
		return pendingDelivery.fallbackPeers.poll();
	}

	boolean add(AID aid, Peer primaryPeer, Set<Peer> peers, CompletableFuture<DeliveryResult> future) {
		return pendingDeliveries.compute(aid, (x, pendingDelivery) -> {
			if (pendingDelivery == null) {
				// if this is the first peer for that aid, it will be the primary peer
				pendingDelivery = new PendingDelivery();
				pendingDelivery.requestedFromPeer = primaryPeer.getNID();
			}
			// add all peers as fallback peers (even primary for automatic retry)
			pendingDelivery.fallbackPeers.addAll(peers);
			pendingDelivery.futures.add(future);
			return pendingDelivery;
		}).wasRequestedBy(primaryPeer);
	}

	void complete(AID aid, DeliveryResult result) {
		PendingDelivery pendingDelivery = pendingDeliveries.get(aid);
		if (pendingDelivery == null) {
			throw new IllegalStateException("Pending delivery for aid '" + aid + "' does not exist");
		}
		pendingDelivery.futures.forEach(future -> future.complete(result));
		pendingDeliveries.remove(aid);
	}

	boolean isPending(AID aid) {
		return pendingDeliveries.containsKey(aid);
	}

	@Override
	public String toString() {
		return "PendingDeliveryState{" +
			"pendingDeliveries=" + pendingDeliveries +
			'}';
	}

	public void reset() {
		this.pendingDeliveries.clear();
	}

	private static final class PendingDelivery {
		private EUID requestedFromPeer;
		private final Queue<Peer> fallbackPeers;
		private final Set<CompletableFuture<DeliveryResult>> futures;

		private PendingDelivery() {
			this.fallbackPeers = new ArrayDeque<>();
			this.futures = new HashSet<>();
		}

		private boolean wasRequestedBy(Peer peer) {
			return requestedFromPeer != null && peer.getNID().equals(requestedFromPeer);
		}

		@Override
		public String toString() {
			return "PendingDelivery{" +
				"requestedFromPeer=" + requestedFromPeer +
				", fallbackPeers=" + fallbackPeers +
				'}';
		}
	}
}
