package com.radixdlt.tempo.delivery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.tempo.AtomAcceptor;

public class PushOnlyDelivererModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder<AtomAcceptor> acceptorMultibinder = Multibinder.newSetBinder(binder(), AtomAcceptor.class);
		acceptorMultibinder.addBinding().to(PushOnlyDeliverer.class);
		Multibinder<AtomDeliverer> delivererMultibinder = Multibinder.newSetBinder(binder(), AtomDeliverer.class);
		acceptorMultibinder.addBinding().to(PushOnlyDeliverer.class);
	}
}
