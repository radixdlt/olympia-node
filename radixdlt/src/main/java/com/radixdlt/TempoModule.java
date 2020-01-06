package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.consensus.tempo.WallclockTimeSupplier;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import org.radix.time.Time;

// FIXME: static dependency on Time
public class TempoModule extends AbstractModule {
	@Override
	protected void configure() {
		// dependencies
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);
		bind(Consensus.class).to(Tempo.class).in(Scopes.SINGLETON);

		bind(Application.class).to(RadixEngineAtomProcessor.class).in(Scopes.SINGLETON);
	}
}
