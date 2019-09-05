package com.radixdlt.tempo.consensus;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.tempo.AtomObserver;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;

public class ConsensusModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder<AtomObserver> observerMultibinder = Multibinder.newSetBinder(binder(), AtomObserver.class);
		observerMultibinder.addBinding().to(ConsensusController.class);

		Multibinder<AtomDiscoverer> discovererMultibinder = Multibinder.newSetBinder(binder(), AtomDiscoverer.class);
		discovererMultibinder.addBinding().to(SampleRetriever.class);

		// dependencies
		bind(SampleNodeSelector.class).to(SimpleSampleNodeSelector.class);
		// FIXME: static dependency on AddressBook through Modules
		bind(AddressBook.class).toProvider(() -> Modules.get(AddressBook.class));
	}
}
