package org.radix.integration;

import org.junit.After;
import org.junit.Before;
import org.radix.atoms.AtomStore;
import org.radix.atoms.sync.AtomSync;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.routing.RoutingHandler;
import org.radix.routing.RoutingStore;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.crypto.Hash;

public class RadixTestWithStores extends RadixTest
{
	@Before
	public void beforeEachRadixTest() throws ModuleException
	{
		Modules.getInstance().start(new DatabaseEnvironment());
		Modules.getInstance().start(clean(new AtomStore()));
		Modules.getInstance().start(clean(new RoutingStore()));
		Modules.getInstance().start(new RoutingHandler());
		Modules.getInstance().start(new AtomSync());
	}

	@After
	public void afterEachRadixTest() throws ModuleException
	{
		safelyStop(Modules.get(AtomSync.class));
		safelyStop(Modules.get(RoutingHandler.class));
		safelyStop(Modules.get(RoutingStore.class));
		safelyStop(Modules.get(AtomStore.class));
		safelyStop(Modules.get(DatabaseEnvironment.class));

		Modules.remove(AtomSync.class);
		Modules.remove(AtomStore.class);
		Modules.remove(DatabaseEnvironment.class);
	}

	public static DatabaseStore clean(DatabaseStore m) throws ModuleException {
		m.reset_impl();
		return m;
	}

	public static void safelyStop(Module m) throws ModuleException {
		if (m != null) {
			Modules.getInstance().stop(m);
		}
	}
}
