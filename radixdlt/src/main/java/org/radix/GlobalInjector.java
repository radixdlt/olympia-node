/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.radixdlt.CerberusModule;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.delivery.LazyRequestDelivererModule;
import com.radixdlt.discovery.IterativeDiscovererModule;
import com.radixdlt.mempool.MempoolModule;
import com.radixdlt.middleware2.MiddlewareModule;
import com.radixdlt.network.NetworkModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.berkeley.BerkeleyStoreModule;
import com.radixdlt.submission.SubmissionControlModule;
import com.radixdlt.universe.Universe;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.network2.addressbook.AddressBookModule;
import org.radix.network2.addressbook.PeerManagerConfiguration;
import org.radix.network2.messaging.MessageCentralModule;
import org.radix.network2.transport.tcp.TCPTransportModule;
import org.radix.network2.transport.udp.UDPTransportModule;
import org.radix.universe.system.LocalSystem;

public class GlobalInjector {

	private Injector injector;

	public GlobalInjector(RuntimeProperties properties, DatabaseEnvironment dbEnv, LocalSystem localSystem, Universe universe) {
		Module lazyRequestDelivererModule = new LazyRequestDelivererModule(properties);
		Module iterativeDiscovererModule = new IterativeDiscovererModule(properties);
		Module berkeleyStoreModule = new BerkeleyStoreModule();
		Module tempoModule = new CerberusModule();
		Module middlewareModule = new MiddlewareModule();
		Module messageCentralModule = new MessageCentralModule(properties);
		Module udpTransportModule = new UDPTransportModule(properties);
		Module tcpTransportModule = new TCPTransportModule(properties);
		Module addressBookModule = new AddressBookModule(dbEnv);
		Module submissionControlModule = new SubmissionControlModule();
		Module mempoolModule = new MempoolModule();
		Module networkModule = new NetworkModule();

		// temporary global module to hook up global things
		Module globalModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(properties);
				bind(DatabaseEnvironment.class).toInstance(dbEnv);
				bind(Serialization.class).toProvider(Serialization::getDefault);
				bind(Events.class).toProvider(Events::getInstance);
				bind(LocalSystem.class).toInstance(localSystem);
				bind(EUID.class).annotatedWith(Names.named("self")).toInstance(localSystem.getNID());
				bind(ECKeyPair.class).annotatedWith(Names.named("self")).toInstance(localSystem.getKeyPair());
				bind(ECPublicKey.class).annotatedWith(Names.named("self")).toInstance(localSystem.getKeyPair().getPublicKey());
				bind(RadixAddress.class).annotatedWith(Names.named("self")).toInstance(RadixAddress.from(universe, localSystem.getKey()));
				bind(Universe.class).toInstance(universe);
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
				tcpTransportModule,
				addressBookModule,
				submissionControlModule,
				mempoolModule,
				networkModule,
				globalModule);
	}

	public Injector getInjector() {
		return injector;
	}
}
