package com.radixdlt.tempo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.radixdlt.TempoModule;
import com.radixdlt.tempo.consensus.ConsensusModule;
import com.radixdlt.tempo.delivery.LazyRequestDelivererModule;
import com.radixdlt.tempo.discovery.IterativeDiscovererModule;
import com.radixdlt.tempo.store.berkeley.BerkeleyStoreModule;
import org.radix.modules.Modules;
import org.radix.network2.messaging.MessageCentral;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

/**
 * Factory for creating {@link Tempo}
 */
public class TempoFactory {
	public Tempo createDefault(RuntimeProperties properties) {
		return createInjector(LocalSystem.getInstance(), Modules.get(MessageCentral.class), properties).getInstance(Tempo.class);
	}

	private Injector createInjector(LocalSystem localSystem, MessageCentral messageCentral, RuntimeProperties properties) {
		return Guice.createInjector(
			new LazyRequestDelivererModule(properties),
			new IterativeDiscovererModule(properties),
			new BerkeleyStoreModule(),
			new ConsensusModule(),
			new TempoModule(localSystem, messageCentral)
		);
	}
}
