package com.radixdlt.tempo.delivery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.tempo.AtomObserver;

public class PushOnlyDelivererModule extends AbstractModule {
	@Override
	protected void configure() {
		Multibinder<AtomObserver> observerMultibinder = Multibinder.newSetBinder(binder(), AtomObserver.class);
		observerMultibinder.addBinding().to(PushOnlyDeliverer.class);
		Multibinder<AtomDeliverer> delivererMultibinder = Multibinder.newSetBinder(binder(), AtomDeliverer.class);
		delivererMultibinder.addBinding().to(PushOnlyDeliverer.class);
	}
}
