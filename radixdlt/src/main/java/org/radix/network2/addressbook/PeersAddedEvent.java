package org.radix.network2.addressbook;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

/**
 * An {@link AddressBookEvent} to let interested parties know that
 * newly discovered peers have been added to the address book.
 */
public final class PeersAddedEvent extends AddressBookEvent {

	private final ImmutableList<Peer> peers;

	PeersAddedEvent(ImmutableList<Peer> peers) {
		this.peers = Objects.requireNonNull(peers);
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

}
