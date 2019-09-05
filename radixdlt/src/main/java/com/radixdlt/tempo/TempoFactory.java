package com.radixdlt.tempo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.radixdlt.TempoModule;
import com.radixdlt.tempo.consensus.ConsensusModule;
import com.radixdlt.tempo.delivery.LazyRequestDelivererModule;
import com.radixdlt.tempo.discovery.IterativeDiscovererModule;
import com.radixdlt.tempo.store.berkeley.BerkeleyStoreModule;
import org.radix.properties.RuntimeProperties;

/**
 * Factory for creating {@link Tempo}
 */
public class TempoFactory {
	public Tempo createDefault(RuntimeProperties properties) {
		return createInjector(properties).getInstance(Tempo.class);
	}

	private Injector createInjector(RuntimeProperties properties) {
		return Guice.createInjector(
			new LazyRequestDelivererModule(properties),
			new IterativeDiscovererModule(properties),
			new BerkeleyStoreModule(),
			new ConsensusModule(),
			new TempoModule()
		);
	}
}
