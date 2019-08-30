package org.radix.network2.addressbook;

import java.util.List;

import org.radix.events.Event;

public abstract class AddressBookEvent extends Event {

	public abstract List<Peer> peers();

}
