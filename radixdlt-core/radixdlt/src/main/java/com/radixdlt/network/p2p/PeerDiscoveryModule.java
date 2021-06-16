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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.ScheduledEventProducerOnRunner;
import com.radixdlt.network.p2p.discovery.DiscoverPeers;
import com.radixdlt.network.p2p.discovery.GetPeers;
import com.radixdlt.network.p2p.discovery.PeerDiscovery;
import com.radixdlt.network.p2p.discovery.PeersResponse;

import java.time.Duration;

public final class PeerDiscoveryModule extends AbstractModule {

	@Override
	protected void configure() {
		final var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(DiscoverPeers.class);
	}

	@ProvidesIntoSet
	public ScheduledEventProducerOnRunner<?> discoverPeersEventProducer(
		EventDispatcher<DiscoverPeers> discoverPeersEventDispatcher,
		P2PConfig config
	) {
		return new ScheduledEventProducerOnRunner<>(
			Runners.P2P_NETWORK,
			discoverPeersEventDispatcher,
			DiscoverPeers::create,
			Duration.ofMillis(500L),
			Duration.ofMillis(config.discoveryInterval())
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> discoverPeersEventProcessor(
		PeerDiscovery peerDiscovery
	) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			DiscoverPeers.class,
			peerDiscovery.discoverPeersEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> getPeersRemoteEventProcessor(
		PeerDiscovery peerDiscovery
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			GetPeers.class,
			peerDiscovery.getPeersRemoteEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> peersResponseRemoteEventProcessor(
		PeerDiscovery peerDiscovery
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeersResponse.class,
			peerDiscovery.peersResponseRemoteEventProcessor()
		);
	}
}
