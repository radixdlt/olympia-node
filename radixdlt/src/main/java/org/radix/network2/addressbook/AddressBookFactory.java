package org.radix.network2.addressbook;

import org.radix.events.Events;

import com.radixdlt.serialization.Serialization;

/**
 * Factory for creating a {@link AddressBook}.
 */
public class AddressBookFactory {

	/**
	 * Create a {@link AddressBook} based on a default configuration.
	 * <p>
	 * Unfortunately need to return the implementation type at this point, as
	 * the module system pretty much requires that the returned type extends
	 * {@code DatabaseStore} right now.
	 *
	 * @param serialization serializer to use when storing data
	 * @param events event system for publishing system events
	 * @return The newly constructed {@link AddressBook}
	 */
	public AddressBookImpl createDefault(Serialization serialization, Events events) {
		return new AddressBookImpl(serialization, events);
	}
}
