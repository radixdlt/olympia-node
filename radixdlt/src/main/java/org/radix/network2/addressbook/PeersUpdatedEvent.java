package org.radix.network2.addressbook;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * An {@link AddressBookEvent} to let interested parties know that
 * peers have been updated in the address book.
 */
public final class PeersUpdatedEvent extends AddressBookEvent {

	private final ImmutableList<Peer> peers;

	PeersUpdatedEvent(ImmutableList<Peer> peers) {
		this.peers = peers;
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

}
