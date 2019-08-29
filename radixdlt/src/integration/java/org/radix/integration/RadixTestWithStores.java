package org.radix.integration;

import com.google.common.collect.ImmutableList;
import com.radixdlt.tempo.Tempo;
import com.radixdlt.tempo.TempoController;
import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.AtomSyncView;
import com.radixdlt.tempo.EdgeSelector;
import com.radixdlt.tempo.delivery.TargetDeliverer;
import org.junit.After;
import org.junit.Before;
import org.radix.atoms.AtomStore;
import org.radix.atoms.sync.AtomSync;
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

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixTestWithStores extends RadixTest
{
	@Before
	public void beforeEachRadixTest() throws ModuleException {
		Modules.getInstance().start(new DatabaseEnvironment());
		Modules.getInstance().start(clean(new RoutingStore()));
		Modules.getInstance().start(new RoutingHandler());

		RuntimeProperties properties = Modules.get(RuntimeProperties.class);
		MessageCentral messageCentral = new MessageCentralFactory().createDefault(properties);
		Modules.put(MessageCentral.class, messageCentral);

		if (Modules.get(RuntimeProperties.class).get("tempo2", false)) {
			TempoController controller = TempoController.builder().build();
			PeerSupplier peerSupplier = mock(PeerSupplier.class);
			when(peerSupplier.getNids()).thenReturn(ImmutableList.of());
			EdgeSelector edgeSelector = mock(EdgeSelector.class);
			when(edgeSelector.selectEdges(any(), any())).thenReturn(ImmutableList.of());
			Tempo tempo = Tempo.defaultBuilderStoreOnly()
				.controller(controller)
				.peerSupplier(peerSupplier)
				.edgeSelector(edgeSelector)
				.targetDeliverer(mock(TargetDeliverer.class))
				.build();
			Modules.getInstance().start(tempo);
		} else {
			Modules.getInstance().start(clean(new AtomStore()));
			Modules.getInstance().start(new AtomSync());
		}
	}

	@After
	public void afterEachRadixTest() throws ModuleException, IOException {
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
			Modules.remove(AtomSyncView.class);
		}
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
