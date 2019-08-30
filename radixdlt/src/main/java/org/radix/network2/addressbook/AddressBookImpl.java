package org.radix.network2.addressbook;

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

import com.radixdlt.utils.Pair;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Network;
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

	private final Lock peersLock = new ReentrantLock();
	private final Map<EUID, Peer>          peersByNid  = new HashMap<>();
	private final Map<TransportInfo, Peer> peersByInfo = new HashMap<>();

	@Inject
	AddressBookImpl(PeerPersistence persistence, Events events) {
		super();
		this.persistence = Objects.requireNonNull(persistence);
		this.events = Objects.requireNonNull(events);

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
		return Locking.withBiFunctionLock(this.peersLock, this::updateSystemInternal, peer, system);
	}

	@Override
	public Peer peer(EUID nid) {
		Peer p = Locking.withFunctionLock(this.peersLock, this.peersByNid::get, nid);
		if (p == null) {
			p = new NidOnlyPeer(nid);
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
		return peers().filter(StandardFilters.recentlyActive());
	}

	// Sends PeersAddedEvents and/or PeersRemoveEvents as required
	private boolean handleUpdatedPeers(Pair<Peer, Peer> updatedPeers) {
		if (updatedPeers != null) {
			if (updatedPeers.getFirst() != null) {
				events.broadcast(new PeersAddedEvent(ImmutableList.of(updatedPeers.getFirst())));
			}
			if (updatedPeers.getSecond() != null) {
				events.broadcast(new PeersRemovedEvent(ImmutableList.of(updatedPeers.getSecond())));
			}
			return updatedPeers.getFirst() != null || updatedPeers.getSecond() != null;
		}
		return false;
	}

	// Needs peersLock held
	// FIXME: double check logic here - especially around NID changes
	private Pair<Peer, Peer> addUpdatePeerInternal(Peer peer) {
		// Handle specially if it's a connection-only peer (no NID)
		if (!peer.hasNID()) {
			// We don't save connection-only peers to the database
			// We don't overwrite if transport already exists
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.putIfAbsent(t, peer) == null)
				.reduce(false, (a, b) -> a | b);
			return changed ? Pair.of(peer, null) : null;
		}

		Peer oldPeer = peersByNid.get(peer.getNID());
		if (oldPeer == null || peer.hasSystem()) {
			// Add new peer
			updatePeerInternal(peer);
			return Pair.of(peer, null);
		}
		// No change
		return null;
	}

	// Needs peersLock held
	// Note that peer must have a nid to get here
	private void updatePeerInternal(Peer peer) {
		EUID nid = peer.getNID();
		if (this.persistence.savePeer(peer)) {
			peersByNid.put(nid, peer);
			// We overwrite transports here
			peer.supportedTransports()
				.forEachOrdered(t -> peersByInfo.put(t, peer));
		}
	}

	// Needs peersLock held
	private Pair<Peer, Peer> removePeerInternal(Peer peer) {
		if (!peer.hasNID()) {
			// We didn't save connection-only peers to the database
			// Only remove transport if it points to specified peer
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.remove(t, peer))
				.reduce(false, (a, b) -> a | b);
			return changed ? Pair.of(null, peer) : null;
		} else {
			EUID nid = peer.getNID();
			Peer oldPeer = peersByNid.remove(nid);
			if (oldPeer != null) {
				// Remove all transports
				peer.supportedTransports().forEachOrdered(peersByInfo::remove);
				if (!this.persistence.deletePeer(nid)) {
					log.error("Failure removing " + oldPeer + " associated with " + nid);
				}
			}
			return Pair.of(null, oldPeer);
		}
	}

	// Needs peersLock held
	private Peer updateSystemInternal(Peer peer, RadixSystem system) {
		Peer newPeer = new PeerWithSystem(peer, system);
		if (!peer.hasNID() || peer.getNID().equals(system.getNID())) {
			// Here if it is basically a new peer or a peer with the same NID
			// This is a simple update or add
			// Only remove transports if it belongs to the specified peer
			peer.supportedTransports().forEachOrdered(t -> this.peersByInfo.remove(t, peer));
			addUpdatePeerInternal(newPeer);
		} else {
			// Peer has somehow changed NID?
			removePeerInternal(peer);
			addUpdatePeerInternal(newPeer);
		}
		return newPeer;
	}

	private boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		if (host != null) {
			if (!Network.getInstance().isWhitelisted(host)) {
				return true;
			}
		}
		return false;
	}
}

