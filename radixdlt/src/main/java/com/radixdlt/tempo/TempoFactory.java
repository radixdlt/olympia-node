package com.radixdlt.tempo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.radixdlt.TempoModule;
import com.radixdlt.tempo.delivery.LazyRequestDelivererModule;
import com.radixdlt.tempo.delivery.PushOnlyDelivererModule;
import com.radixdlt.tempo.discovery.IterativeDiscovererModule;
import com.radixdlt.tempo.store.berkeley.BerkeleyStoreModule;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

/**
 * Factory for creating {@link Tempo}
 */
public class TempoFactory {
	public Tempo createDefault(LocalSystem localSystem, RuntimeProperties properties) {
		return createInjector(localSystem, properties).getInstance(Tempo.class);
	}

	private Injector createInjector(LocalSystem localSystem, RuntimeProperties properties) {
		return Guice.createInjector(
			new LazyRequestDelivererModule(properties),
			new PushOnlyDelivererModule(),
			new IterativeDiscovererModule(properties),
			new BerkeleyStoreModule(),
			new TempoModule(localSystem)
		);
	}
}
