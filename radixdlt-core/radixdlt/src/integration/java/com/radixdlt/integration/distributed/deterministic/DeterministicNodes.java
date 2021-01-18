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
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.DeterministicMessageProcessor;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.assertj.core.util.Lists;

/**
 * BFT Nodes treated as a single unit where one message is processed at a time.
 */
public final class DeterministicNodes {
	private static final Logger log = LogManager.getLogger();

	private final ImmutableList<Injector> nodeInstances;
	private final ControlledSenderFactory senderFactory;
	private final ImmutableBiMap<BFTNode, Integer> nodeLookup;
	private final List<String> nodeNames = Lists.newArrayList();
	private final List<DeterministicMessageProcessor> messageProcessors = Lists.newArrayList();

	public DeterministicNodes(
		List<BFTNode> nodes,
		ControlledSenderFactory senderFactory,
		Module baseModule,
		Module overrideModule
	) {
		this.senderFactory = senderFactory;
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
					bind(ControlledSenderFactory.class).toInstance(senderFactory);
				}
			},
			new DeterministicEnvironmentModule(),
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
		this.nodeNames.clear();
		this.messageProcessors.clear();
		for (int index = 0; index < this.nodeInstances.size(); index++) {
			Injector injector = nodeInstances.get(index);
			DeterministicMessageProcessor processor = injector.getInstance(DeterministicMessageProcessor.class);
			String bftNode = " " + this.nodeLookup.inverse().get(index);
			ThreadContext.put("bftNode", bftNode);
			try {
				processor.start();
			} finally {
				ThreadContext.remove("bftNode");
			}
			this.nodeNames.add(bftNode);
			this.messageProcessors.add(processor);
		}
	}

	public void handleMessage(Timed<ControlledMessage> timedNextMsg) {
		ControlledMessage nextMsg = timedNextMsg.value();
		int senderIndex = nextMsg.channelId().senderIndex();
		int receiverIndex = nextMsg.channelId().receiverIndex();
		BFTNode sender = this.nodeLookup.inverse().get(senderIndex);
		ThreadContext.put("bftNode", this.nodeNames.get(receiverIndex));
		try {
			log.debug("Received message {} at {}", nextMsg, timedNextMsg.time());
			this.messageProcessors.get(receiverIndex).handleMessage(sender, nextMsg.message());
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.nodeInstances.get(nodeIndex).getInstance(SystemCounters.class);
	}
}
