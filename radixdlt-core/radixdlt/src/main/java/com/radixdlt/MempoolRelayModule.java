package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolRelayer;

/**
 * Module responsible for sending/receiving mempool messages to/from other nodes.
 */
public final class MempoolRelayModule extends AbstractModule {

	@Override
	public void configure() {
		bind(MempoolRelayer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<MempoolAddSuccess> mempoolAddedCommandEventProcessor(MempoolRelayer mempoolRelayer) {
		return mempoolRelayer.mempoolAddedCommandEventProcessor();
	}

	@Provides
	private RemoteEventProcessor<MempoolAddSuccess> remoteEventProcessor(
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		return (node, cmd) -> mempoolAddEventDispatcher.dispatch(MempoolAdd.create(cmd.getCommand()));
	}
}
