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

package com.radixdlt.network.addressbook;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.properties.RuntimeProperties;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.discovery.Whitelist;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;
import org.radix.utils.Locking;

/**
 * Implementation of {@link AddressBook}.
 * Note that a persistence layer may be specified so that clients do not have
 * to wait for a lengthy discovery process to complete on restarting.
 */
public class AddressBookImpl implements AddressBook {
	private static final Logger log = LogManager.getLogger();

	private final PeerPersistence persistence;
	private final long recencyThreshold;

	private final Lock peersLock = new ReentrantLock();

	// Used for NID lookups
	private final Map<EUID, PeerWithSystem> peersByNid = Maps.newHashMap();

	// Used for forward lookups when only transport is known (eg bootstrap nodes)
	private final Map<TransportInfo, PeerWithSystem> peersByInfo = Maps.newHashMap();

	// Used for reverse lookups when only source is known (eg incoming messages)
	private final BiMap<TransportInfo, PeerWithSystem> peersBySource = HashBiMap.create();

	private final Whitelist whitelist;
	private final TimeSupplier timeSupplier;

	private final Lock emittersLock = new ReentrantLock();
	private final Set<ObservableEmitter<AddressBookEvent>> emitters = Sets.newIdentityHashSet();

	@Inject
	AddressBookImpl(PeerPersistence persistence, RuntimeProperties properties, TimeSupplier timeSupplier) {
		super();
		this.persistence = Objects.requireNonNull(persistence);
		this.recencyThreshold = properties.get("addressbook.recency_ms", 60L * 1000L);
		this.whitelist = Whitelist.from(properties);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);

		this.persistence.forEachPersistedPeer(peer -> {
			this.peersByNid.put(peer.getNID(), peer);
			peer.supportedTransports()
				.forEachOrdered(ti -> this.peersByInfo.put(ti, peer));
		});

		// Clean out any existing non-whitelisted peers from the store (whitelist may have changed since last execution)
		// Take copy to avoid CoMoException
		ImmutableList<PeerWithSystem> allPeers = ImmutableList.copyOf(peersByNid.values());
		for (PeerWithSystem peer : allPeers) {
			// Maybe consider making whitelist per transport at some point?
			if (peer.supportedTransports().anyMatch(this::hostNotWhitelisted)) {
				log.info("Deleting {}, as not whitelisted", peer);
				removePeer(peer.getNID());
			}
		}
	}

	private static final class PeerUpdates {
		final PeerWithSystem added;
		final PeerWithSystem removed;
		final PeerWithSystem updated;
		final PeerWithSystem exists;

		static PeerUpdates add(PeerWithSystem peer) {
			return new PeerUpdates(peer, null, null, null);
		}

		static PeerUpdates remove(PeerWithSystem peer) {
			return new PeerUpdates(null, peer, null, null);
		}

		static PeerUpdates update(PeerWithSystem peer) {
			return new PeerUpdates(null, null, peer, null);
		}

		static PeerUpdates addAndRemove(PeerWithSystem added, PeerWithSystem removed) {
			return new PeerUpdates(added, removed, null, null);
		}

		static PeerUpdates existing(PeerWithSystem existing) {
			return new PeerUpdates(null, null, null, existing);
		}

		private PeerUpdates(PeerWithSystem added, PeerWithSystem removed, PeerWithSystem updated, PeerWithSystem exists) {
			this.added = added;
			this.removed = removed;
			this.updated = updated;
			this.exists = exists;
		}
	}

	@Override
	public boolean removePeer(EUID nid) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::removePeerInternal, nid));
	}

	@Override
	public PeerWithSystem addOrUpdatePeer(Optional<PeerWithSystem> peer, RadixSystem system, TransportInfo source) {
		return peer.map(p -> updatePeer(p, system, source)).orElseGet(() -> newPeer(system, source));
	}

	@Override
	public Optional<PeerWithSystem> peer(EUID nid) {
		PeerWithSystem peer = Locking.withFunctionLock(this.peersLock, this.peersByNid::get, nid);
		updateActiveTime(peer);
		return Optional.ofNullable(peer);
	}

	@Override
	public Optional<PeerWithSystem> peer(TransportInfo transportInfo) {
		PeerWithSystem peer = Locking.withFunctionLock(this.peersLock, this::getPeerByTransportInfo, transportInfo);
		updateActiveTime(peer);
		return Optional.ofNullable(peer);
	}

	@Override
	public Stream<PeerWithSystem> peers() {
		return Locking.withSupplierLock(this.peersLock, () -> ImmutableSet.copyOf(this.peersByInfo.values())).stream();
	}

	@Override
	public Stream<PeerWithSystem> recentPeers() {
		return peers().filter(StandardFilters.recentlyActive(recencyThreshold));
	}

	@Override
	public Stream<EUID> nids() {
		return Locking.withSupplierLock(this.peersLock, () -> ImmutableSet.copyOf(this.peersByNid.keySet())).stream();
	}

	@Override
	public Observable<AddressBookEvent> peerUpdates() {
		return Observable.create(emitter ->
			Locking.withConsumerLock(this.peersLock, this::addEmitter, emitter)
		);
	}

	@Override
	public void close() throws IOException {
		Locking.withLock(this.emittersLock, () -> {
			this.emitters.forEach(ObservableEmitter::onComplete);
			this.emitters.clear();
		});
		this.persistence.close();
	}

	// Sends PeersAddedEvents and/or PeersRemoveEvents as required
	private boolean handleUpdatedPeers(PeerUpdates peerUpdates) {
		if (peerUpdates != null) {
			Locking.withLock(this.emittersLock, () -> {
				if (peerUpdates.added != null) {
					PeersAddedEvent pae = new PeersAddedEvent(ImmutableList.of(peerUpdates.added));
					emitters.forEach(e -> e.onNext(pae));
				}
				if (peerUpdates.removed != null) {
					PeersRemovedEvent pre = new PeersRemovedEvent(ImmutableList.of(peerUpdates.removed));
					emitters.forEach(e -> e.onNext(pre));
				}
				if (peerUpdates.updated != null) {
					PeersUpdatedEvent pue = new PeersUpdatedEvent(ImmutableList.of(peerUpdates.updated));
					emitters.forEach(e -> e.onNext(pue));
				}
			});
			return !(peerUpdates.added == null && peerUpdates.removed == null && peerUpdates.updated == null);
		}
		return false;
	}

	// Needs peersLock held
	private PeerWithSystem getPeerByTransportInfo(TransportInfo transportInfo) {
		PeerWithSystem peer = this.peersBySource.get(transportInfo);
		if (peer == null) {
			peer = this.peersByInfo.get(transportInfo);
		}
		return peer;
	}

	// Needs peersLock held
	private PeerUpdates addUpdatePeerInternal(PeerWithSystem peer, TransportInfo source) {
		PeerWithSystem oldPeer = peersByNid.get(peer.getNID());
		if (oldPeer == null || !Objects.equals(oldPeer.getSystem(), peer.getSystem())) {
			// Add new peer or update old peer
			if (updatePeerInternal(peer, source)) {
				return oldPeer == null ? PeerUpdates.add(peer) : PeerUpdates.update(peer);
			}
		} else {
			// No system change, just update source
			updateSource(peer, source);
		}
		return oldPeer == null ? null : PeerUpdates.existing(oldPeer);
	}

	// Needs peersLock held
	private boolean updatePeerInternal(PeerWithSystem peer, TransportInfo source) {
		if (this.persistence.savePeer(peer)) {
			peersByNid.put(peer.getNID(), peer);
			// We overwrite transports here
			peer.supportedTransports()
				.forEachOrdered(t -> peersByInfo.put(t, peer));
			peersBySource.put(source, peer);
			return true;
		}
		log.error("Failure saving {}", peer);
		return false;
	}

	// Needs peersLock held
	private PeerUpdates removePeerInternal(EUID nid) {
		PeerWithSystem oldPeer = peersByNid.remove(nid);
		if (oldPeer != null) {
			removeTransports(oldPeer);
			if (!this.persistence.deletePeer(nid)) {
				log.error("Persistence failure removing {}", oldPeer);
			}
			return PeerUpdates.remove(oldPeer);
		}
		return null;
	}

	// Needs peersLock held
	private void removeTransports(PeerWithSystem peer) {
		Iterator<Map.Entry<TransportInfo, PeerWithSystem>> i = this.peersByInfo.entrySet().iterator();
		while (i.hasNext()) {
			PeerWithSystem testPeer = i.next().getValue();
			if (peer.equals(testPeer)) {
				i.remove();
			}
		}
		this.peersBySource.inverse().remove(peer);
	}

	// Needs peersLock held
	private PeerUpdates updateSystemInternal(PeerWithSystem peer, RadixSystem system, TransportInfo source) {
		if (peer.getNID().equals(system.getNID())) {
			if (!Objects.equals(peer.getSystem(), system)) {
				// Here if old system does not match the updated system
				// This is basically remove old, add new
				removeTransports(peer);
				return addUpdatePeerInternal(new PeerWithSystem(peer, system), source);
			} else {
				// No system change, just update source, as it may have changed
				updateSource(peer, source);
			}
		} else {
			// Peer has somehow changed NID?
			PeerWithSystem newPeer = new PeerWithSystem(peer, system);
			removePeerInternal(peer.getNID());
			addUpdatePeerInternal(newPeer, source);
			return PeerUpdates.addAndRemove(newPeer, peer);
		}
		// No change
		return null;
	}

	// No locks required
	private PeerWithSystem updatePeer(PeerWithSystem oldPeer, RadixSystem system, TransportInfo source) {
		PeerUpdates updates = Locking.withSupplierLock(this.peersLock, () -> updateSystemInternal(oldPeer, system, source));
		handleUpdatedPeers(updates);
		if (updates != null) {
			if (updates.exists != null) {
				return updates.exists;
			}
			if (updates.updated != null) {
				return updates.updated;
			}
			if (updates.added != null) {
				return updates.added;
			}
		}
		return oldPeer;
	}

	// Update source for peer, needs peersLock held
	private void updateSource(PeerWithSystem peer, TransportInfo source) {
		this.peersBySource.inverse().remove(peer);
		this.peersBySource.put(source, peer);
	}

	// No locks required
	private PeerWithSystem newPeer(RadixSystem system, TransportInfo source) {
		PeerWithSystem newPeer = new PeerWithSystem(system);
		handleUpdatedPeers(Locking.withSupplierLock(this.peersLock, () -> addUpdatePeerInternal(newPeer, source)));
		return newPeer;
	}

	private boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		return (host != null) && !whitelist.isWhitelisted(host);
	}

	private void updateActiveTime(Peer peer) {
		if (peer != null) {
			peer.setTimestamp(Timestamps.ACTIVE, this.timeSupplier.currentTime());
		}
	}

	private void addEmitter(ObservableEmitter<AddressBookEvent> emitter) {
		Locking.withLock(this.emittersLock, () -> {
			this.emitters.add(emitter);
			emitter.setCancellable(() -> this.removeEmitter(emitter));
			emitter.onNext(new PeersAddedEvent(ImmutableList.copyOf(this.peersByInfo.values())));
		});
	}

	private void removeEmitter(ObservableEmitter<?> emitter) {
		Locking.withLock(this.emittersLock, () -> this.emitters.remove(emitter));
	}
}
