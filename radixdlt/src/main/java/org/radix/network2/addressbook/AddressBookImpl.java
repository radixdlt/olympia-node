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

package org.radix.network2.addressbook;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.common.EUID;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.discovery.Whitelist;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.utils.Locking;
import org.radix.universe.system.RadixSystem;

/**
 * Implementation of {@link AddressBook}.
 * Note that a persistence layer may be specified so that clients do not have
 * to wait for a lengthy discovery process to complete on restarting.
 */
// FIXME: Static dependency on Network.getInstance().isWhitelisted(...)
public class AddressBookImpl implements AddressBook {
	private static final Logger log = Logging.getLogger("addressbook");

	private final PeerPersistence persistence;
	private final Events events;
	private final long recencyThreshold;

	private final Lock peersLock = new ReentrantLock();
	private final Map<EUID, Peer>          peersByNid  = new HashMap<>();
	private final Map<TransportInfo, Peer> peersByInfo = new HashMap<>();
	private final Whitelist whitelist;

	@Inject
	AddressBookImpl(PeerPersistence persistence, Events events, Universe universe, RuntimeProperties properties) {
		super();
		this.persistence = Objects.requireNonNull(persistence);
		this.events = Objects.requireNonNull(events);
		this.recencyThreshold = Objects.requireNonNull(universe).getPlanck();
		this.whitelist = Whitelist.from(properties);

		this.persistence.forEachPersistedPeer(peer -> {
			this.peersByNid.put(peer.getNID(), peer);
			peer.supportedTransports()
				.forEachOrdered(ti -> this.peersByInfo.put(ti, peer));
		});

		// Clean out any existing non-whitelisted peers from the store (whitelist may have changed since last execution)
		// Take copy to avoid CoMoException
		ImmutableList<Peer> allPeers = ImmutableList.copyOf(peersByNid.values());
		for (Peer peer : allPeers) {
			// Maybe consider making whitelist per transport at some point?
			if (peer.supportedTransports().anyMatch(this::hostNotWhitelisted)) {
				log.info("Deleting " + peer + ", as not whitelisted");
				removePeer(peer);
			}
		}
	}

	private static final class PeerUpdates {
		final Peer added;
		final Peer removed;
		final Peer updated;

		static PeerUpdates added(Peer peer) {
			return new PeerUpdates(peer, null, null);
		}

		public static PeerUpdates removed(Peer peer) {
			return new PeerUpdates(null, peer, null);
		}

		public static PeerUpdates updated(Peer peer) {
			return new PeerUpdates(null, null, peer);
		}

		public static PeerUpdates addAndRemove(Peer added, Peer removed) {
			return new PeerUpdates(added, removed, null);
		}

		private PeerUpdates(Peer added, Peer removed, Peer updated) {
			this.added = added;
			this.removed = removed;
			this.updated = updated;
		}
	}

	@Override
	public boolean addPeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::addUpdatePeerInternal, peer));
	}

	@Override
	public boolean removePeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::removePeerInternal, peer));
	}

	@Override
	public boolean updatePeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::addUpdatePeerInternal, peer));
	}

	@Override
	public Peer updatePeerSystem(Peer peer, RadixSystem system) {
		PeerUpdates updates = Locking.withBiFunctionLock(this.peersLock, this::updateSystemInternal, peer, system);
		handleUpdatedPeers(updates);
		if (updates != null) {
			if (updates.updated != null) {
				return updates.updated;
			}
			if (updates.added != null) {
				return updates.added;
			}
		}
		return peer;
	}

	@Override
	public Peer peer(EUID nid) {
		Peer p = Locking.withFunctionLock(this.peersLock, this.peersByNid::get, nid);
		if (p == null) {
			p = new PeerWithNid(nid);
			addPeer(p);
		}
		return p;
	}

	@Override
	public Peer peer(TransportInfo transportInfo) {
		Peer p = Locking.withFunctionLock(this.peersLock, this.peersByInfo::get, transportInfo);
		if (p == null) {
			p = new PeerWithTransport(transportInfo);
			addPeer(p);
		}
		return p;
	}

	@Override
	public Stream<Peer> peers() {
		// FIXME: Think about how to do this in a not so copying way
		return Locking.withSupplierLock(this.peersLock, () -> ImmutableSet.copyOf(this.peersByInfo.values())).stream();
	}

	@Override
	public Stream<Peer> recentPeers() {
		return peers().filter(StandardFilters.recentlyActive(recencyThreshold));
	}

	@Override
	public Stream<EUID> nids() {
		// FIXME: Think about how to do this in a not so copying way
		return Locking.withSupplierLock(this.peersLock, () -> ImmutableSet.copyOf(this.peersByNid.keySet())).stream();
	}

	// Sends PeersAddedEvents and/or PeersRemoveEvents as required
	private boolean handleUpdatedPeers(PeerUpdates peerUpdates) {
		if (peerUpdates != null) {
			if (peerUpdates.added != null) {
				events.broadcast(new PeersAddedEvent(ImmutableList.of(peerUpdates.added)));
			}
			if (peerUpdates.removed != null) {
				events.broadcast(new PeersRemovedEvent(ImmutableList.of(peerUpdates.removed)));
			}
			if (peerUpdates.updated != null) {
				events.broadcast(new PeersUpdatedEvent(ImmutableList.of(peerUpdates.updated)));
			}
			return !(peerUpdates.added == null && peerUpdates.removed == null && peerUpdates.updated == null);
		}
		return false;
	}

	// Needs peersLock held
	// FIXME: double check logic here - especially around NID changes
	private PeerUpdates addUpdatePeerInternal(Peer peer) {
		// Handle specially if it's a connection-only peer (no NID)
		if (!peer.hasNID()) {
			// We don't save connection-only peers to the database
			// We don't overwrite if transport already exists
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.putIfAbsent(t, peer) == null)
				.reduce(false, (a, b) -> a | b);
			return changed ? PeerUpdates.added(peer) : null;
		}

		Peer oldPeer = peersByNid.get(peer.getNID());
		if (oldPeer == null || !Objects.equals(oldPeer.getSystem(), peer.getSystem())) {
			// Add new peer or update old peer
			if (updatePeerInternal(peer)) {
				return oldPeer == null ? PeerUpdates.added(peer) : PeerUpdates.updated(peer);
			}
		}
		// No change
		return null;
	}

	// Needs peersLock held
	// Note that peer must have a nid to get here
	private boolean updatePeerInternal(Peer peer) {
		if (this.persistence.savePeer(peer)) {
			peersByNid.put(peer.getNID(), peer);
			// We overwrite transports here
			peer.supportedTransports()
				.forEachOrdered(t -> peersByInfo.put(t, peer));
			return true;
		}
		log.error("Failure saving " + peer);
		return false;
	}

	// Needs peersLock held
	private PeerUpdates removePeerInternal(Peer peer) {
		if (!peer.hasNID()) {
			// We didn't save connection-only peers to the database
			// Only remove transport if it points to specified peer
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.remove(t, peer))
				.reduce(false, (a, b) -> a | b);
			return changed ? PeerUpdates.removed(peer) : null;
		} else {
			EUID nid = peer.getNID();
			Peer oldPeer = peersByNid.remove(nid);
			if (oldPeer != null) {
				// Remove all transports
				oldPeer.supportedTransports().forEachOrdered(peersByInfo::remove);
				if (!this.persistence.deletePeer(nid)) {
					log.error("Failure removing " + oldPeer);
				}
				return PeerUpdates.removed(oldPeer);
			}
		}
		return null;
	}

	// Needs peersLock held
	private PeerUpdates updateSystemInternal(Peer peer, RadixSystem system) {
		if (!peer.hasNID() || peer.getNID().equals(system.getNID())) {
			if (!Objects.equals(peer.getSystem(), system)) {
				// Here if:
				// 1. Old peer has no NID at all, or does have a nid, and nids match
				// 2. Old system does not match the updated system
				// This is a simple update or add
				// Only remove transports if it belongs to the specified peer
				Peer newPeer = new PeerWithSystem(peer, system);
				peer.supportedTransports().forEachOrdered(t -> this.peersByInfo.remove(t, peer));
				return addUpdatePeerInternal(newPeer);
			}
		} else {
			// Peer has somehow changed NID?
			Peer newPeer = new PeerWithSystem(peer, system);
			removePeerInternal(peer);
			addUpdatePeerInternal(newPeer);
			return PeerUpdates.addAndRemove(newPeer, peer);
		}
		// No change
		return null;
	}

	private boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		if (host != null) {
			if (!whitelist.isWhitelisted(host)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void close() throws IOException {
		this.persistence.close();
	}
}

