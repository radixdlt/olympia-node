/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.simulation.configuration;

import com.google.common.collect.Sets;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.simulation.network.SimulationNetwork;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.LatencyProvider;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Latency Provider which makes it easy to drop certain messages
 */
public final class DroppingLatencyProvider implements LatencyProvider {
	private final Set<Predicate<MessageInTransit>> droppers = Sets.newConcurrentHashSet();
	private final AtomicReference<LatencyProvider> base = new AtomicReference<>();

	public DroppingLatencyProvider() {
		this.base.set(msg -> SimulationNetwork.DEFAULT_LATENCY);
	}

	public DroppingLatencyProvider copyOf() {
		DroppingLatencyProvider provider = new DroppingLatencyProvider();
		provider.setBase(this.base.get());
		provider.droppers.addAll(this.droppers);
		return provider;
	}

	public void setBase(LatencyProvider base) {
		this.base.set(base);
	}

	public void addDropper(Predicate<MessageInTransit> dropper) {
		this.droppers.add(dropper);
	}

	public void crashNode(ECPublicKey node) {
		droppers.add(msg -> msg.getReceiver().equals(node) || msg.getSender().equals(node));
	}

	@Override
	public int nextLatency(MessageInTransit msg) {
		if (droppers.stream().anyMatch(f -> f.test(msg))) {
			return -1; // -1 Drops the message
		}

		return base.get().nextLatency(msg);
	}
}
