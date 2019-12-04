package org.radix;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.TempoModule;
import com.radixdlt.middleware2.MiddlewareModule;
import com.radixdlt.tempo.delivery.LazyRequestDelivererModule;
import com.radixdlt.tempo.discovery.IterativeDiscovererModule;
import com.radixdlt.tempo.store.berkeley.BerkeleyStoreModule;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;

public class GlobalInjector {

	private Injector injector;

	public GlobalInjector() {
		Module lazyRequestDelivererModule = new LazyRequestDelivererModule(Modules.get(RuntimeProperties.class));
		Module iterativeDiscovererModule = new IterativeDiscovererModule(Modules.get(RuntimeProperties.class));
		Module berkeleyStoreModule = new BerkeleyStoreModule();
		Module tempoModule = new TempoModule();
		Module middlewareModule = new MiddlewareModule();

		injector = Guice.createInjector(
				lazyRequestDelivererModule,
				iterativeDiscovererModule,
				berkeleyStoreModule,
				tempoModule,
				middlewareModule);
	}

	public Injector getInjector() {
		return injector;
	}
}
