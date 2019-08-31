package com.radixdlt.tempo.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.radix.events.Events;
import org.radix.properties.RuntimeProperties;

public class IterativeDiscovererModule extends AbstractModule {
	private final IterativeDiscovererConfiguration configuration;

	public IterativeDiscovererModule(RuntimeProperties properties) {
		this(IterativeDiscovererConfiguration.fromRuntimeProperties(properties));
	}

	public IterativeDiscovererModule(IterativeDiscovererConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	protected void configure() {
		// main target
		Multibinder<AtomDiscoverer> discovererMultibinder = Multibinder.newSetBinder(binder(), AtomDiscoverer.class);
		discovererMultibinder.addBinding().to(IterativeDiscoverer.class);

		// dependencies
		// FIXME remove static dependency on Events
		bind(Events.class).toProvider(Events::getInstance);
		bind(IterativeDiscovererConfiguration.class).toInstance(configuration);
	}
}
