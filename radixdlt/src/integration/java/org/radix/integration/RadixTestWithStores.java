package org.radix.integration;

import com.radixdlt.tempo.AtomSyncView;
import com.radixdlt.tempo.AtomSynchroniser;
import com.radixdlt.tempo.Tempo;
import org.junit.After;
import org.junit.Before;
import org.radix.atoms.AtomStore;
import org.radix.atoms.sync.AtomSync;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import org.radix.routing.RoutingHandler;
import org.radix.routing.RoutingStore;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.crypto.Hash;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixTestWithStores extends RadixTest
{
	@Before
	public void beforeEachRadixTest() throws ModuleException
	{
		Modules.getInstance().start(new DatabaseEnvironment());
		Modules.getInstance().start(clean(new RoutingStore()));
		Modules.getInstance().start(new RoutingHandler());
		if (Modules.get(RuntimeProperties.class).get("tempo2", false)) {
			AtomSynchroniser mockSynchroniser = mock(AtomSynchroniser.class);
			when(mockSynchroniser.getLegacyAdapter()).thenReturn(mock(AtomSyncView.class));
			Tempo tempo = Tempo.defaultBuilderWithoutSynchroniser()
				.synchroniser(mockSynchroniser).build();
			Modules.getInstance().start(tempo);
		} else {
			Modules.getInstance().start(clean(new AtomStore()));
			Modules.getInstance().start(new AtomSync());
		}
	}

	@After
	public void afterEachRadixTest() throws ModuleException
	{
		safelyStop(Modules.get(RoutingHandler.class));
		safelyStop(Modules.get(RoutingStore.class));
		safelyStop(Modules.get(DatabaseEnvironment.class));
		if (Modules.get(RuntimeProperties.class).get("tempo2", false)) {
			Modules.get(Tempo.class).stop_impl();
			Modules.get(Tempo.class).reset_impl();
			Modules.remove(Tempo.class);
		} else {
			safelyStop(Modules.get(AtomStore.class));
			safelyStop(Modules.get(AtomSync.class));
			Modules.remove(AtomSync.class);
			Modules.remove(AtomStore.class);
		}
		Modules.remove(DatabaseEnvironment.class);
	}

	private static DatabaseStore clean(DatabaseStore m) throws ModuleException {
		m.reset_impl();
		return m;
	}

	public static void safelyStop(Module m) throws ModuleException {
		if (m != null) {
			Modules.getInstance().stop(m);
		}
	}
}
