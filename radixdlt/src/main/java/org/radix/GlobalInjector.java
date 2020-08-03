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
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.radixdlt.CerberusModule;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.MempoolModule;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.middleware2.MiddlewareModule;
import com.radixdlt.middleware2.network.NetworkModule;
import com.radixdlt.network.addressbook.AddressBookModule;
import com.radixdlt.network.addressbook.PeerManagerConfiguration;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.transport.tcp.TCPTransportModule;
import com.radixdlt.network.transport.udp.UDPTransportModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.berkeley.BerkeleyStoreModule;
import com.radixdlt.universe.Universe;

import javax.inject.Inject;
import javax.inject.Provider;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.universe.system.LocalSystem;

public class GlobalInjector {

	private Injector injector;

	public GlobalInjector(RuntimeProperties properties, DatabaseEnvironment dbEnv, Universe universe) {
		// temporary global module to hook up global things
		Module globalModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(properties);
				bind(DatabaseEnvironment.class).toInstance(dbEnv);
				bind(Universe.class).toInstance(universe);

				bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);

				bind(LocalSystem.class).toProvider(LocalSystemProvider.class).in(Scopes.SINGLETON);

				bind(EUID.class).annotatedWith(Names.named("self")).toProvider(SelfNidProvider.class);
				bind(ECKeyPair.class).annotatedWith(Names.named("self")).toProvider(SelfKeyPairProvider.class);
				bind(ECPublicKey.class).annotatedWith(Names.named("self")).toProvider(SelfPublicKeyProvider.class);
				bind(RadixAddress.class).annotatedWith(Names.named("self")).toProvider(SelfAddressProvider.class);
				bind(BFTNode.class).annotatedWith(Names.named("self")).toProvider(SelfBFTNodeProvider.class);

				bind(Serialization.class).toProvider(DefaultSerialization::getInstance);
				bind(Events.class).toProvider(Events::getInstance);

				bind(PeerManagerConfiguration.class).toInstance(PeerManagerConfiguration.fromRuntimeProperties(properties));
			}
		};

		injector = Guice.createInjector(
			new BerkeleyStoreModule(),
			new CerberusModule(properties),
			new MiddlewareModule(),
			new MessageCentralModule(properties),
			new UDPTransportModule(properties),
			new TCPTransportModule(properties),
			new AddressBookModule(dbEnv),
			new HostIpModule(properties),
			new MempoolModule(),
			new NetworkModule(),
			globalModule
		);
	}

	public Injector getInjector() {
		return injector;
	}

	static class SelfNidProvider implements Provider<EUID> {
		private final LocalSystem localSystem;

		@Inject
		SelfNidProvider(LocalSystem localSystem) {
			this.localSystem = localSystem;
		}

		@Override
		public EUID get() {
			return this.localSystem.getNID();
		}
	}

	static class SelfKeyPairProvider implements Provider<ECKeyPair> {
		private final LocalSystem localSystem;

		@Inject
		SelfKeyPairProvider(LocalSystem localSystem) {
			this.localSystem = localSystem;
		}

		@Override
		public ECKeyPair get() {
			return this.localSystem.getKeyPair();
		}
	}

	static class SelfPublicKeyProvider implements Provider<ECPublicKey> {
		private final LocalSystem localSystem;

		@Inject
		SelfPublicKeyProvider(LocalSystem localSystem) {
			this.localSystem = localSystem;
		}

		@Override
		public ECPublicKey get() {
			return this.localSystem.getKeyPair().getPublicKey();
		}
	}

	static class SelfAddressProvider implements Provider<RadixAddress> {
		private final Universe universe;
		private final LocalSystem localSystem;

		@Inject
		SelfAddressProvider(Universe universe, LocalSystem localSystem) {
			this.universe = universe;
			this.localSystem = localSystem;
		}

		@Override
		public RadixAddress get() {
			return new RadixAddress((byte) this.universe.getMagic(), this.localSystem.getKey());
		}
	}

	static class SelfBFTNodeProvider implements Provider<BFTNode> {
		private final LocalSystem localSystem;

		@Inject
		SelfBFTNodeProvider(LocalSystem localSystem) {
			this.localSystem = localSystem;
		}

		@Override
		public BFTNode get() {
			return BFTNode.create(this.localSystem.getKey());
		}
	}

	static class LocalSystemProvider implements Provider<LocalSystem> {
		private final RuntimeProperties properties;
		private final Universe universe;
		private final HostIp hostIp;
		private final InfoSupplier infoSupplier;

		@Inject
		public LocalSystemProvider(InfoSupplier infoSupplier, RuntimeProperties properties, Universe universe, HostIp hostIp) {
			this.infoSupplier = infoSupplier;
			this.properties = properties;
			this.universe = universe;
			this.hostIp = hostIp;
		}

		@Override
		public LocalSystem get() {
			String host = this.hostIp.hostIp()
				.orElseThrow(() -> new IllegalStateException("Unable to determine host IP"));

			return LocalSystem.create(infoSupplier, this.properties, this.universe, host);
		}
	}
}
