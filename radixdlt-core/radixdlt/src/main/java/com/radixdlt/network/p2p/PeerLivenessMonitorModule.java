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
import com.radixdlt.network.p2p.liveness.PeerLivenessMonitor;
import com.radixdlt.network.p2p.liveness.PeerPingTimeout;
import com.radixdlt.network.p2p.liveness.PeersLivenessCheckTrigger;
import com.radixdlt.network.p2p.liveness.Ping;
import com.radixdlt.network.p2p.liveness.Pong;

import java.time.Duration;

public final class PeerLivenessMonitorModule extends AbstractModule {

	@Override
	protected void configure() {
		final var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(PeersLivenessCheckTrigger.class);
		eventBinder.addBinding().toInstance(PeerPingTimeout.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> peersLivenessCheckTriggerEventProcessor(
		PeerLivenessMonitor peerLivenessMonitor
	) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeersLivenessCheckTrigger.class,
			peerLivenessMonitor.peersLivenessCheckTriggerEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> peerPingTimeoutEventProcessor(
		PeerLivenessMonitor peerLivenessMonitor
	) {
		return new EventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			PeerPingTimeout.class,
			peerLivenessMonitor.pingTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> peerPingRemoteEventProcessor(
		PeerLivenessMonitor peerLivenessMonitor
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			Ping.class,
			peerLivenessMonitor.pingRemoteEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> pongPingRemoteEventProcessor(
		PeerLivenessMonitor peerLivenessMonitor
	) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.P2P_NETWORK,
			Pong.class,
			peerLivenessMonitor.pongRemoteEventProcessor()
		);
	}

	@ProvidesIntoSet
	public ScheduledEventProducerOnRunner<?> peersLivenessCheckTriggerEventProducer(
		EventDispatcher<PeersLivenessCheckTrigger> peersLivenessCheckTriggerEventDispatcher,
		P2PConfig config
	) {
		return new ScheduledEventProducerOnRunner<>(
			Runners.P2P_NETWORK,
			peersLivenessCheckTriggerEventDispatcher,
			PeersLivenessCheckTrigger::create,
			Duration.ofMillis(config.peerLivenessCheckInterval()),
			Duration.ofMillis(config.peerLivenessCheckInterval())
		);
	}
}
