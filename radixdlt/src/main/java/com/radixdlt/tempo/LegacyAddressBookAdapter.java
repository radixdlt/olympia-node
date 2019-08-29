package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.Event;
import org.radix.events.EventListener;
import org.radix.events.Events;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network.peers.events.PeerDisconnectedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

// TODO remove when addressbook has been fully integrated
public class LegacyAddressBookAdapter implements LegacyAddressBook {
	private final Supplier<PeerHandler> peerHandler;
	private final Events events;
	private final List<LegacyAddressBookListener> listeners;

	public LegacyAddressBookAdapter(Supplier<PeerHandler> peerHandler, Events events) {
		this.peerHandler = Objects.requireNonNull(peerHandler);
		this.events = Objects.requireNonNull(events);
		this.listeners = Collections.synchronizedList(new ArrayList<>());

		this.events.register(PeerAvailableEvent.class, event -> peerAdded(((PeerAvailableEvent) event).getPeer()));
		this.events.register(PeerDisconnectedEvent.class, event -> peerRemoved(((PeerDisconnectedEvent) event).getPeer()));
	}

	private void peerAdded(Peer peer) {
		listeners.forEach(listener -> listener.onPeerAdded(Collections.singleton(peer)));
	}

	private void peerRemoved(Peer peer) {
		listeners.forEach(listener -> listener.onPeerRemoved(Collections.singleton(peer)));
	}

	@Override
	public boolean contains(EUID nid) {
		try {
			return !peerHandler.get().getPeers(PeerHandler.PeerDomain.NETWORK, Collections.singleton(nid)).isEmpty();
		} catch (DatabaseException e) {
			throw new TempoException(e);
		}
	}

	@Override
	public void addListener(LegacyAddressBookListener listener) {
		listeners.add(listener);
	}

	@Override
	public boolean removeListener(LegacyAddressBookListener listener) {
		return listeners.remove(listener);
	}
}
