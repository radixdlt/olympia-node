package com.radixdlt.tempo.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
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
		Multibinder<AtomDiscoverer> discovererMultibinder = Multibinder.newSetBinder(binder(), AtomDiscoverer.class);
		discovererMultibinder.addBinding().to(IterativeDiscoverer.class);

		bind(IterativeDiscovererConfiguration.class).toInstance(configuration);
	}
}
