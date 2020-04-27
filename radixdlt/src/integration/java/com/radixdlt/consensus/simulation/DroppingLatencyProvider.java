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

package com.radixdlt.consensus.simulation;

import com.google.common.collect.Sets;
import com.radixdlt.consensus.GetVertexResponse;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.network.GetVertexRequestMessage;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.LatencyProvider;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.MessageInTransit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Latency Provider which makes it easy to drop certain messages
 */
public final class DroppingLatencyProvider implements LatencyProvider {
	private final Set<Predicate<MessageInTransit>> droppingFunctions = Sets.newConcurrentHashSet();
	private final AtomicReference<LatencyProvider> base = new AtomicReference<>();
	private final AtomicBoolean disableSync = new AtomicBoolean(false);

	public DroppingLatencyProvider() {
		this.base.set(msg -> TestEventCoordinatorNetwork.DEFAULT_LATENCY);
		// Implement it in this way for now so that sync disable is mutable
		this.droppingFunctions.add(msg -> disableSync.get()
			&& (msg.getContent() instanceof GetVertexResponse || msg.getContent() instanceof GetVertexRequestMessage));
	}

	public DroppingLatencyProvider copyOf() {
		DroppingLatencyProvider provider = new DroppingLatencyProvider();
		provider.setBase(this.base.get());
		provider.droppingFunctions.addAll(this.droppingFunctions);
		return provider;
	}

	public void setBase(LatencyProvider base) {
		this.base.set(base);
	}

	public void crashNode(ECPublicKey node) {
		droppingFunctions.add(msg -> msg.getReceiver().equals(node) || msg.getSender().equals(node));
	}

	public void disableSync(boolean disableSync) {
		this.disableSync.set(disableSync);
	}

	@Override
	public int nextLatency(MessageInTransit msg) {
		if (droppingFunctions.stream().anyMatch(f -> f.test(msg))) {
			return -1; // -1 Drops the message
		}

		return base.get().nextLatency(msg);
	}
}
