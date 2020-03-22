/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
		return "PendingDeliveryState{" + "pendingDeliveries=" + pendingDeliveries + '}';
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
			return "PendingDelivery{" + "requestedFromPeer=" + requestedFromPeer + ", fallbackPeers=" + fallbackPeers + '}';
		}
	}
}
