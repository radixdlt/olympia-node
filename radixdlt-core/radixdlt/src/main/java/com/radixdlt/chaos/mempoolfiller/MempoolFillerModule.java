package com.radixdlt.chaos.mempoolfiller;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.universe.Universe;

public final class MempoolFillerModule extends AbstractModule {
	@Override
	public void configure() {
		bind(ECKeyPair.class).annotatedWith(MempoolFillerKey.class).toProvider(ECKeyPair::generateNew).in(Scopes.SINGLETON);
		bind(MempoolFiller.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(MempoolFillerUpdate.class);
		eventBinder.addBinding().toInstance(ScheduledMempoolFill.class);
	}

	@ProvidesIntoSet
	private StateReducer<?, ?> mempoolFillerWallet(
		@NativeToken RRI tokenRRI,
		@MempoolFillerKey RadixAddress mempoolFillerAddress
	) {
		return new InMemoryWalletReducer(tokenRRI, mempoolFillerAddress);
	}

	@Provides
	@MempoolFillerKey
	private RadixAddress mempoolFillerAddress(@MempoolFillerKey ECPublicKey pubKey, Universe universe) {
		return new RadixAddress((byte) universe.getMagic(), pubKey);
	}

	@Provides
	@MempoolFillerKey
	private ECPublicKey mempoolFillerKey(@MempoolFillerKey ECKeyPair keyPair) {
		return keyPair.getPublicKey();
	}

	@Provides
	public EventProcessor<MempoolFillerUpdate> messageFloodUpdateEventProcessor(MempoolFiller mempoolFiller) {
		return mempoolFiller.messageFloodUpdateProcessor();
	}

	@Provides
	public EventProcessor<ScheduledMempoolFill> scheduledMessageFloodEventProcessor(MempoolFiller mempoolFiller) {
		return mempoolFiller.scheduledMempoolFillEventProcessor();
	}
}
