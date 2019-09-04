package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Attestor;
import com.radixdlt.tempo.Owned;
import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.PeerSupplierAdapter;
import com.radixdlt.tempo.Resource;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.SingleThreadedScheduler;
import com.radixdlt.tempo.Tempo;
import com.radixdlt.tempo.TempoAttestor;
import com.radixdlt.tempo.WallclockTimeSupplier;
import com.radixdlt.tempo.consensus.ConsensusReceptor;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.LCCursorStore;
import com.radixdlt.tempo.store.SampleStore;
import com.radixdlt.tempo.store.TempoAtomStore;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

// FIXME: remove static dependency on Time
// FIXME: remove static dependency on Events
public class TempoModule extends AbstractModule {
	// FIXME: manual injection of MessageCentral should be done automatically
	private final LocalSystem localSystem;
	private final MessageCentral messageCentral;

	public TempoModule(LocalSystem localSystem, MessageCentral messageCentral) {
		this.localSystem = localSystem;
		this.messageCentral = messageCentral;
	}

	@Override
	protected void configure() {
		// TODO bind Ledger interface to Tempo when ready to consume in application level
		bind(ConsensusReceptor.class).to(Tempo.class);

		bind(LocalSystem.class).annotatedWith(Names.named("self")).toInstance(localSystem);
		bind(EUID.class).annotatedWith(Names.named("self")).toInstance(localSystem.getNID());

		// TODO ugly way of assigning resource "ownership", should be cleaner
		Multibinder<Resource> ownedResourcesBinder = Multibinder.newSetBinder(binder(), Resource.class, Owned.class);
		ownedResourcesBinder.addBinding().to(TempoAtomStore.class);
		ownedResourcesBinder.addBinding().to(CommitmentStore.class);
		ownedResourcesBinder.addBinding().to(LCCursorStore.class);
		ownedResourcesBinder.addBinding().to(SampleStore.class);

		// dependencies
		bind(MessageCentral.class).toInstance(messageCentral);
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(Attestor.class).to(TempoAttestor.class);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);

		// FIXME: static dependency on modules for address book
		bind(PeerSupplier.class).toProvider(() -> new PeerSupplierAdapter(() -> Modules.get(AddressBook.class))).in(Singleton.class);
	}
}
