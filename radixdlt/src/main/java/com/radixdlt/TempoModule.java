package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.tempo.Owned;
import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.PeerSupplierAdapter;
import com.radixdlt.tempo.Resource;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.SingleThreadedScheduler;
import com.radixdlt.tempo.Tempo;
import com.radixdlt.tempo.WallclockTimeSupplier;
import com.radixdlt.tempo.store.LCCursorStore;
import com.radixdlt.tempo.store.LedgerEntryStore;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

// FIXME: static dependency on Time
// FIXME: static dependency on Events
// FIXME: static dependency on LocalSystem
// FIXME: static dependency on MessageCentral through Modules
// FIXME: static dependency on AddressBook through Modules
public class TempoModule extends AbstractModule {
	@Override
	protected void configure() {
		// TODO bind Ledger interface to Tempo when ready to consume in application level
		LocalSystem localSystem = LocalSystem.getInstance();
		bind(LocalSystem.class).annotatedWith(Names.named("self")).toInstance(localSystem);
		bind(EUID.class).annotatedWith(Names.named("self")).toInstance(localSystem.getNID());

		// dependencies
		bind(MessageCentral.class).toInstance(Modules.get(MessageCentral.class));
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);
		bind(Ledger.class).to(Tempo.class).in(Scopes.SINGLETON);

		bind(PeerSupplier.class).toProvider(() -> new PeerSupplierAdapter(() -> Modules.get(AddressBook.class))).in(Singleton.class);
	}
}
