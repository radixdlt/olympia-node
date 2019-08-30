package com.radixdlt.tempo.delivery;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import org.radix.network.peers.Peer;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

final class RequestDeliveryState {
	private final Map<AID, EUID> pendingDeliveries;
	private final Map<AID, Queue<Peer>> fallbackDeliveries;

	RequestDeliveryState() {
		this.pendingDeliveries = new ConcurrentHashMap<>();
		this.fallbackDeliveries = new ConcurrentHashMap<>();
	}

	void addFallback(AID aid, Peer peer) {
		fallbackDeliveries.computeIfAbsent(aid, x -> new ArrayDeque<>()).offer(peer);
	}

	Optional<Peer> getFallback(AID aid) {
		Queue<Peer> fallbacks = fallbackDeliveries.get(aid);
		if (fallbacks == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(fallbacks.poll());
	}

	void addRequest(Collection<AID> aids, EUID nid) {
		aids.forEach(aid -> pendingDeliveries.put(aid, nid));
	}

	void removeRequest(AID aid) {
		pendingDeliveries.remove(aid);
		fallbackDeliveries.remove(aid);
	}

	boolean isPending(AID aid) {
		return pendingDeliveries.containsKey(aid);
	}

	@Override
	public String toString() {
		return "RequestDeliveryState{" +
			"pendingDeliveries=" + pendingDeliveries +
			'}';
	}
}
