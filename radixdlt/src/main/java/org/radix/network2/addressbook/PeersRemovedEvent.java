package org.radix.network2.addressbook;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class PeersRemovedEvent implements AddressBookEvent {

	private final ImmutableList<Peer> peers;

	public PeersRemovedEvent(Iterable<Peer> peers) {
		this.peers = ImmutableList.copyOf(peers);
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

}
