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

package com.radixdlt.consensus;

import com.google.common.collect.Sets;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.network.GetVertexRequestMessage;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.LatencyProvider;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.MessageInTransit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class DroppingLatencyProvider implements LatencyProvider {
	private final Set<Predicate<MessageInTransit>> droppingFunctions = Sets.newConcurrentHashSet();
	private AtomicReference<LatencyProvider> base = new AtomicReference<>();

	public DroppingLatencyProvider() {
		this.base.set(msg -> TestEventCoordinatorNetwork.DEFAULT_LATENCY);
	}

	public void setBase(LatencyProvider base) {
		this.base.set(base);
	}

	public void crashNode(ECPublicKey node) {
		droppingFunctions.add(msg -> msg.getReceiver().equals(node) || msg.getSender().equals(node));
	}

	public void disableSync() {
		droppingFunctions.add(msg -> msg.getContent() instanceof GetVertexResponse);
		droppingFunctions.add(msg -> msg.getContent() instanceof GetVertexRequestMessage);
	}

	@Override
	public int nextLatency(MessageInTransit msg) {
		if (droppingFunctions.stream().anyMatch(f -> f.test(msg))) {
			return -1;
		}

		return base.get().nextLatency(msg);
	}
}
