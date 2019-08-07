package org.radix.network2.addressbook;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class PeersAddedEvent implements AddressBookEvent {

	private final ImmutableList<Peer> peers;

	public PeersAddedEvent(Iterable<Peer> peers) {
		this.peers = ImmutableList.copyOf(peers);
	}

	@Override
	public List<Peer> getPeers() {
		return this.peers;
	}

}
