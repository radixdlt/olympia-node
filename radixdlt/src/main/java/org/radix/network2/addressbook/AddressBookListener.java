package org.radix.network2.addressbook;

@FunctionalInterface
public interface AddressBookListener {

	/**
	 * Handle notification of an {@link AddressBookEvent}.
	 *
	 * @param event The address book event we are being notified of
	 */
	void handleEvent(AddressBookEvent event);

}
