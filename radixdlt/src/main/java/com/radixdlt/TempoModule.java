package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Attestor;
import com.radixdlt.tempo.EdgeSelector;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.SingleThreadedScheduler;
import com.radixdlt.tempo.TempoAttestor;
import com.radixdlt.tempo.WallclockTimeSupplier;
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

		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(Attestor.class).to(TempoAttestor.class);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);

//		bind(EdgeSelector.class).to(SimpleEdgeSelector)
	}
}
