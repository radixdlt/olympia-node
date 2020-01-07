package com.radixdlt.store.berkeley;

import com.google.inject.AbstractModule;
import com.radixdlt.store.CursorStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;

public class BerkeleyStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(LedgerEntryStore.class).to(BerkeleyLedgerEntryStore.class);
		bind(LedgerEntryStoreView.class).to(BerkeleyLedgerEntryStore.class);
		bind(CursorStore.class).to(BerkeleyCursorStore.class);
	}
}
