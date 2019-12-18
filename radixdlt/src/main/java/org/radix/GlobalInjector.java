package org.radix;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.radixdlt.TempoModule;
import com.radixdlt.common.EUID;
import com.radixdlt.delivery.LazyRequestDelivererModule;
import com.radixdlt.discovery.IterativeDiscovererModule;
import com.radixdlt.middleware2.MiddlewareModule;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.berkeley.BerkeleyStoreModule;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Event;
import org.radix.events.Events;
import org.radix.network2.addressbook.AddressBookModule;
import org.radix.network2.addressbook.PeerManagerConfiguration;
import org.radix.network2.messaging.MessageCentralModule;
import org.radix.network2.transport.udp.UDPTransportModule;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

public class GlobalInjector {

	private Injector injector;

	public GlobalInjector(RuntimeProperties properties, DatabaseEnvironment dbEnv, LocalSystem localSystem) {
		Module lazyRequestDelivererModule = new LazyRequestDelivererModule(properties);
		Module iterativeDiscovererModule = new IterativeDiscovererModule(properties);
		Module berkeleyStoreModule = new BerkeleyStoreModule();
		Module tempoModule = new TempoModule();
		Module middlewareModule = new MiddlewareModule();
		Module messageCentralModule = new MessageCentralModule(properties);
		Module udpTransportModule = new UDPTransportModule(properties);
		Module addressBookModule = new AddressBookModule(dbEnv);

		// temporary global module to hook up global things
		Module globalModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(properties);
				bind(DatabaseEnvironment.class).toInstance(dbEnv);
				bind(Serialization.class).toProvider(Serialization::getDefault);
				bind(Events.class).toProvider(Events::getInstance);
				bind(LocalSystem.class).annotatedWith(Names.named("self")).toInstance(localSystem);
				bind(EUID.class).annotatedWith(Names.named("self")).toInstance(localSystem.getNID());
				bind(PeerManagerConfiguration.class).toInstance(PeerManagerConfiguration.fromRuntimeProperties(properties));
			}
		};

		injector = Guice.createInjector(
				lazyRequestDelivererModule,
				iterativeDiscovererModule,
				berkeleyStoreModule,
				tempoModule,
				middlewareModule,
				messageCentralModule,
				udpTransportModule,
				addressBookModule,
				globalModule);
	}

	public Injector getInjector() {
		return injector;
	}
}
