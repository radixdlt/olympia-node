package com.radixdlt.statecomputer.forks;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.ledger.LedgerUpdate;

public final class MockedForksEpochStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(InMemoryForksEpochStore.Store.class).toInstance(new InMemoryForksEpochStore.Store());
		bind(ForksEpochStore.class).to(InMemoryForksEpochStore.class).in(Scopes.SINGLETON);
	}

	@Singleton
	@ProvidesIntoSet
	public EventProcessorOnDispatch<?> eventProcessor(InMemoryForksEpochStore inMemoryForksEpochStore) {
		return new EventProcessorOnDispatch<>(
			LedgerUpdate.class,
			inMemoryForksEpochStore.ledgerUpdateEventProcessor()
		);
	}
}
