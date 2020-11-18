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

package com.radixdlt.integration.distributed.simulation.invariants.consensus;

import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventProcessor;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class NodeTimeouts {
	public static class NodeTimeout {
		private final BFTNode node;
		private final Timeout timeout;

		private NodeTimeout(BFTNode node, Timeout timeout) {
			this.node = node;
			this.timeout = timeout;
		}

		public BFTNode node() {
			return node;
		}

		public Timeout timeout() {
			return timeout;
		}
	}

	private final Set<Consumer<NodeTimeout>> consumers = new HashSet<>();

	public void addListener(Consumer<NodeTimeout> nodeTimeoutConsumer) {
		this.consumers.add(nodeTimeoutConsumer);
	}

	public EventProcessor<Timeout> processor(BFTNode node) {
		return t -> consumers.forEach(c -> c.accept(new NodeTimeout(node, t)));
	}
}
