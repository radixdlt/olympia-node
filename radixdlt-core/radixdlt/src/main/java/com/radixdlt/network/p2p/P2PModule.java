/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.network.p2p;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.p2p.PendingOutboundChannelsManager.PeerOutboundConnectionTimeout;
import com.radixdlt.network.p2p.addressbook.AddressBookPersistence;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrapImpl;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.store.berkeley.BerkeleyAddressBookPersistence;

public final class P2PModule extends AbstractModule {

	private final RuntimeProperties properties;

	public P2PModule(RuntimeProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void configure() {
		final var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(PeerEvent.class);
		eventBinder.addBinding().toInstance(PeerOutboundConnectionTimeout.class);

		bind(PeersView.class).to(PeerManagerPeersView.class);
		bind(PeerOutboundBootstrap.class).to(PeerOutboundBootstrapImpl.class);
		bind(AddressBookPersistence.class).to(BerkeleyAddressBookPersistence.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> peerManagerPeerEventProcessor(PeerManager peerManager) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeerEvent.class,
			peerManager.peerEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> pendingOutboundChannelsManagerPeerEventProcessor(
		PendingOutboundChannelsManager pendingOutboundChannelsManager
	) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeerEvent.class,
			pendingOutboundChannelsManager.peerEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> peerOutboundConnectionTimeoutEventProcessor(
		PendingOutboundChannelsManager pendingOutboundChannelsManager
	) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeerOutboundConnectionTimeout.class,
			pendingOutboundChannelsManager.peerOutboundConnectionTimeoutEventProcessor()
		);
	}

	@Provides
	public P2PConfig p2pConfig() {
		return P2PConfig.fromRuntimeProperties(this.properties);
	}

	@Provides
	@Self
	public RadixNodeUri selfUri(@Self ECPublicKey selfKey, HostIp hostIp, P2PConfig p2pConfig) {
		final var host = hostIp.hostIp().orElseThrow(() -> new IllegalStateException("Unable to determine host IP"));
		final var port = p2pConfig.broadcastPort();
		return RadixNodeUri.fromPubKeyAndAddress(selfKey, host, port);
	}
}
