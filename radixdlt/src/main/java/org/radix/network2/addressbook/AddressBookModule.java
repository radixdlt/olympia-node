package org.radix.network2.addressbook;

import org.radix.events.Events;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.serialization.Serialization;

/**
 * Guice configuration for {@link AddressBook}.
 */
final class AddressBookModule extends AbstractModule {

	AddressBookModule() {
		// Nothing
	}

	@Override
	protected void configure() {
		// The main target
		bind(AddressBook.class).to(AddressBookImpl.class);

		// AddressBookImpl dependencies
		// See addressBookePersistenceProvider
		bind(Events.class).toProvider(Events::getInstance);

		// AddressBookPersistence dependencies
		bind(Serialization.class).toProvider(Serialization::getDefault);
	}

	@Provides
	@Singleton
	PeerPersistence addressBookPersistenceProvider(Serialization serialization) throws ModuleException {
		AddressBookPersistence persistence = new AddressBookPersistence(serialization);
		Modules.getInstance().start(persistence);
		return persistence;
	}
}
