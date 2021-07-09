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

package com.radixdlt.integration.distributed.deterministic;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * BFT Nodes treated as a single unit where one message is processed at a time.
 */
public final class DeterministicNodes {
	private static final Logger log = LogManager.getLogger();

	private final ImmutableList<Injector> nodeInstances;
	private final DeterministicNetwork network;
	private final ImmutableBiMap<BFTNode, Integer> nodeLookup;

	public DeterministicNodes(
		List<BFTNode> nodes,
		DeterministicNetwork network,
		Module baseModule,
		Module overrideModule
	) {
		this.network = network;
		this.nodeLookup = Streams.mapWithIndex(
			nodes.stream(),
			(node, index) -> Pair.of(node, (int) index)
		).collect(ImmutableBiMap.toImmutableBiMap(Pair::getFirst, Pair::getSecond));
		this.nodeInstances = nodes.stream()
			.map(node -> createBFTInstance(node, baseModule, overrideModule))
			.collect(ImmutableList.toImmutableList());
	}

	private Injector createBFTInstance(BFTNode self, Module baseModule, Module overrideModule) {
		Module module = Modules.combine(
			new AbstractModule() {
				@Override
				public void configure() {
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bind(Environment.class).toInstance(network.createSender(self));
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
				}
			},
			baseModule
		);
		if (overrideModule != null) {
			module = Modules.override(module).with(overrideModule);
		}
		return Guice.createInjector(module);
	}

	public int numNodes() {
		return this.nodeInstances.size();
	}

	public void start() {
		for (int index = 0; index < this.nodeInstances.size(); index++) {
			Injector injector = nodeInstances.get(index);
			var processor = injector.getInstance(DeterministicProcessor.class);

			ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
			try {
				processor.start();
			} finally {
				ThreadContext.remove("self");
			}
		}
	}

	public void handleMessage(Timed<ControlledMessage> timedNextMsg) {
		ControlledMessage nextMsg = timedNextMsg.value();
		int senderIndex = nextMsg.channelId().senderIndex();
		int receiverIndex = nextMsg.channelId().receiverIndex();
		BFTNode sender = this.nodeLookup.inverse().get(senderIndex);

		var injector = nodeInstances.get(receiverIndex);
		ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
		try {
			log.debug("Received message {} at {}", nextMsg, timedNextMsg.time());
			nodeInstances.get(receiverIndex).getInstance(DeterministicProcessor.class)
				.handleMessage(sender, nextMsg.message(), nextMsg.typeLiteral());
		} finally {
			ThreadContext.remove("self");
		}
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.nodeInstances.get(nodeIndex).getInstance(SystemCounters.class);
	}
}
