package com.radixdlt.tempo.store.berkeley;

import com.google.inject.AbstractModule;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.LCCursorStore;
import com.radixdlt.tempo.store.SampleStore;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.database.DatabaseEnvironment;
import org.radix.modules.Modules;
import org.radix.utils.SystemProfiler;

public class BerkeleyStoreModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(TempoAtomStore.class).to(BerkeleyTempoAtomStore.class);
		bind(TempoAtomStoreView.class).to(BerkeleyTempoAtomStore.class);
		bind(CommitmentStore.class).to(BerkeleyCommitmentStore.class);
		bind(LCCursorStore.class).to(BerkeleyLCCursorStore.class);
		bind(SampleStore.class).to(BerkeleySampleStore.class);

		// FIXME: remove static dependency on modules
		bind(DatabaseEnvironment.class).toProvider(() -> Modules.get(DatabaseEnvironment.class));
		bind(Serialization.class).toProvider(Serialization::getDefault);
		bind(SystemProfiler.class).toProvider(SystemProfiler::getInstance);
	}
}
