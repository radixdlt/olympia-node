package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.consensus.tempo.WallclockTimeSupplier;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import org.radix.modules.Modules;
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
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);
		bind(Consensus.class).to(Tempo.class).in(Scopes.SINGLETON);

		bind(Application.class).to(RadixEngineAtomProcessor.class).in(Scopes.SINGLETON);
	}
}
