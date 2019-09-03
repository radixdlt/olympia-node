package com.radixdlt.tempo.consensus;

import com.google.inject.AbstractModule;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;

public class ConsensusModule extends AbstractModule {
	@Override
	protected void configure() {

		// dependencies
		bind(SampleNodeSelector.class).to(SimpleSampleNodeSelector.class);
		// FIXME: static dependency on AddressBook through Modules
		bind(AddressBook.class).toProvider(() -> Modules.get(AddressBook.class));
	}
}
