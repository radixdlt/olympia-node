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

package com.radixdlt.network.p2p.test;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.deterministic.DeterministicMessageProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.network.p2p.P2PConfig;
import io.reactivex.rxjava3.schedulers.Timed;
import org.apache.logging.log4j.ThreadContext;

public class DeterministicP2PNetworkTest {
	protected P2PTestNetworkRunner testNetworkRunner;

	protected P2PConfig defaultConfig() {
		return P2PConfig.of(10, 10, 1000L, 1000, 1000L, 1000L, 30000, "unused", 1, 255);
	}

	protected void setupTestRunner(int numNodes, P2PConfig p2pConfig) throws Exception {
		this.testNetworkRunner = P2PTestNetworkRunner.create(numNodes, p2pConfig);
	}

	protected void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	protected Timed<ControlledMessage> processNext() {
		final var msg = testNetworkRunner.getDeterministicNetwork().nextMessage();
		final var nodeIndex = msg.value().channelId().receiverIndex();
		final var injector = testNetworkRunner.getNode(nodeIndex).injector;
		withThreadCtx(injector, () ->
			injector.getInstance(DeterministicMessageProcessor.class)
				.handleMessage(msg.value().origin(), msg.value().message())
		);
		return msg;
	}

	private void withThreadCtx(Injector injector, Runnable r) {
		ThreadContext.put("bftNode", " " + injector.getInstance(Key.get(BFTNode.class, Self.class)));
		try {
			r.run();
		} finally {
			ThreadContext.remove("bftNode");
		}
	}
}
