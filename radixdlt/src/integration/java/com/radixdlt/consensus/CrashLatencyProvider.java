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
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.LatencyProvider;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.MessageInTransit;
import java.util.Set;

public class CrashLatencyProvider implements LatencyProvider {
	private final Set<ECPublicKey> crashed = Sets.newConcurrentHashSet();
	private final int latency;

	public CrashLatencyProvider(int latency) {
		this.latency = latency;
	}

	public void crashNode(ECPublicKey node) {
		crashed.add(node);
	}

	@Override
	public int nextLatency(MessageInTransit msg) {
		if (crashed.contains(msg.getReceiver()) || crashed.contains(msg.getSender())) {
			return -1;
		}

		return latency;
	}
}
