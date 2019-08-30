package com.radixdlt.tempo.delivery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.tempo.Owned;
import com.radixdlt.tempo.Resource;
import org.radix.properties.RuntimeProperties;

public class LazyRequestDelivererModule extends AbstractModule {
	private final LazyRequestDelivererConfiguration configuration;

	public LazyRequestDelivererModule(RuntimeProperties properties) {
		this(LazyRequestDelivererConfiguration.fromRuntimeProperties(properties));
	}

	public LazyRequestDelivererModule(LazyRequestDelivererConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	protected void configure() {
		Multibinder<AtomDeliverer> delivererMultibinder = Multibinder.newSetBinder(binder(), AtomDeliverer.class);
		delivererMultibinder.addBinding().to(LazyRequestDeliverer.class);
		bind(RequestDeliverer.class).to(LazyRequestDeliverer.class);

		Multibinder<Resource> ownedResourcesBinder = Multibinder.newSetBinder(binder(), Resource.class, Owned.class);
		ownedResourcesBinder.addBinding().to(LazyRequestDeliverer.class);

		bind(LazyRequestDelivererConfiguration.class).toInstance(configuration);
	}
}
