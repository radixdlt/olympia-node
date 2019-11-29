package org.radix.integration;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.Tempo;
import com.radixdlt.tempo.TempoAttestor;
import com.radixdlt.tempo.consensus.Consensus;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.store.LedgerEntryStore;
import com.radixdlt.tempo.store.berkeley.BerkeleyStoreModule;
import org.junit.After;
import org.junit.Before;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageCentralFactory;
import org.radix.properties.RuntimeProperties;
import org.radix.routing.RoutingHandler;
import org.radix.routing.RoutingStore;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;

import static org.mockito.Mockito.mock;

public class RadixTestWithStores extends RadixTest
{
	protected Injector injector;

	@Before
	public void beforeEachRadixTest() throws ModuleException {
		Modules.getInstance().start(new DatabaseEnvironment());
		Modules.getInstance().start(clean(new RoutingStore()));
		Modules.getInstance().start(new RoutingHandler());

		RuntimeProperties properties = Modules.get(RuntimeProperties.class);
		MessageCentral messageCentral = new MessageCentralFactory().createDefault(properties);
		Modules.put(MessageCentral.class, messageCentral);

		EUID self = LocalSystem.getInstance().getNID();
		injector = Guice.createInjector(
				new AbstractModule() {
					@Override
					protected void configure() {
						bind(EUID.class).annotatedWith(Names.named("self")).toInstance(self);
					}
				},
				new BerkeleyStoreModule()
		);

		LedgerEntryStore atomStore = injector.getInstance(LedgerEntryStore.class);
		Tempo tempo = new Tempo(
				self,
				atomStore,
			mock(Consensus.class),
				new TempoAttestor(LocalSystem.getInstance(), Serialization.getDefault(), System::currentTimeMillis),
				ImmutableSet.of(atomStore),
				ImmutableSet.of(),
				mock(RequestDeliverer.class),
				ImmutableSet.of()
		);
			Modules.put(Tempo.class, tempo);
			Modules.put(LedgerEntryStore.class, atomStore);
	}

	@After
	public void afterEachRadixTest() throws ModuleException, IOException {
		safelyStop(Modules.get(RoutingHandler.class));
		safelyStop(Modules.get(RoutingStore.class));

		Modules.get(Tempo.class).close();
		Modules.get(Tempo.class).reset();
		Modules.remove(Tempo.class);
		Modules.remove(LedgerEntryStore.class);

		safelyStop(Modules.get(DatabaseEnvironment.class));
		Modules.remove(DatabaseEnvironment.class);

		MessageCentral messageCentral = Modules.get(MessageCentral.class);
		messageCentral.close();
		Modules.remove(MessageCentral.class);
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
