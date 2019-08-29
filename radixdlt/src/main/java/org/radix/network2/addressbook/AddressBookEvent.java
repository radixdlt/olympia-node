package org.radix.network2.addressbook;

import java.util.List;

import org.radix.events.Event;

/**
 * Base class for events produced by {@code AddressBook}.
 */
public abstract class AddressBookEvent extends Event {

	/**
	 * Returns the {@link Peer} objects affected by this event.
	 *
	 * @return the {@link Peer} objects affected by this event
	 */
	public abstract List<Peer> peers();

}
