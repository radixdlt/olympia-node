package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Attestor;
import com.radixdlt.tempo.EdgeSelector;
import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.PeerSupplierAdapter;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.SimpleEdgeSelector;
import com.radixdlt.tempo.SingleThreadedScheduler;
import com.radixdlt.tempo.TempoAttestor;
import com.radixdlt.tempo.WallclockTimeSupplier;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

public class TempoModule extends AbstractModule {
	private final LocalSystem localSystem;

	public TempoModule(LocalSystem localSystem) {
		this.localSystem = localSystem;
	}

	@Override
	protected void configure() {
		bind(LocalSystem.class).annotatedWith(Names.named("self")).toInstance(localSystem);
		bind(EUID.class).annotatedWith(Names.named("self")).toProvider(localSystem::getNID);

		// FIXME: static dependency on modules for messagecentral
		bind(MessageCentral.class).toProvider(() -> Modules.get(MessageCentral.class));
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(Attestor.class).to(TempoAttestor.class);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);

		bind(EdgeSelector.class).to(SimpleEdgeSelector.class);
		// FIXME: static dependency on modules for address book
		bind(PeerSupplier.class).toProvider(() -> new PeerSupplierAdapter(() -> Modules.get(AddressBook.class))).in(Singleton.class);
	}
}
