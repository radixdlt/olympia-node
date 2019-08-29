package org.radix.network2.addressbook;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * An {@link AddressBookEvent} to let interested parties know that
 * newly discovered peers have been removed from the address book.
 */
public final class PeersRemovedEvent extends AddressBookEvent {

	private final ImmutableList<Peer> peers;

	PeersRemovedEvent(ImmutableList<Peer> peers) {
		this.peers = peers;
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

}
