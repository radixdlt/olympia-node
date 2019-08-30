package org.radix.network2.addressbook;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

public final class PeersAddedEvent extends AddressBookEvent {

	private final ImmutableList<Peer> peers;

	public PeersAddedEvent(Iterable<Peer> peers) {
		this.peers = ImmutableList.copyOf(peers);
	}

	public PeersAddedEvent(ImmutableList<Peer> peers) {
		this.peers = Objects.requireNonNull(peers);
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

}
