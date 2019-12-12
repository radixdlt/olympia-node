package com.radixdlt.store.berkeley;

import com.google.inject.AbstractModule;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CursorStore;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import org.radix.database.DatabaseEnvironment;
import org.radix.modules.Modules;

public class BerkeleyStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(LedgerEntryStore.class).to(BerkeleyLedgerEntryStore.class);
		bind(LedgerEntryStoreView.class).to(BerkeleyLedgerEntryStore.class);
		bind(CursorStore.class).to(BerkeleyCursorStore.class);

		// FIXME: remove static dependency on modules for DatabaseEnvironment
		bind(DatabaseEnvironment.class).toProvider(() -> Modules.get(DatabaseEnvironment.class));
		// FIXME: remove static dependency on modules for Serialization
		bind(Serialization.class).toProvider(Serialization::getDefault);
	}
}
