package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolRelayer;

public class MempoolRelayModule extends AbstractModule {

	@Override
	public void configure() {
		bind(MempoolRelayer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<MempoolAddSuccess> mempoolAddedCommandEventProcessor(MempoolRelayer mempoolRelayer) {
		return mempoolRelayer.mempoolAddedCommandEventProcessor();
	}
}
