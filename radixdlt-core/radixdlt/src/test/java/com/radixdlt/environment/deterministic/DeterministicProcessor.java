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

package com.radixdlt.environment.deterministic;

import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class DeterministicProcessor implements DeterministicMessageProcessor {
	private final BFTNode self;
	private final Map<Class<?>, List<EventProcessorOnRunner<?>>> processors;
	private final Map<TypeLiteral<?>, List<EventProcessorOnRunner<?>>> typeLiteralProcessors;
	private final Map<Class<?>, List<RemoteEventProcessorOnRunner<?>>> remoteProcessors;

	@Inject
	public DeterministicProcessor(
		@Self BFTNode self,
		Set<EventProcessorOnRunner<?>> processorOnRunners,
		Set<RemoteEventProcessorOnRunner<?>> remoteEventProcessorOnRunners
	) {
		this.self = self;
		this.processors = processorOnRunners.stream()
			.filter(r -> r.getEventClass().isPresent())
			.collect(Collectors.<EventProcessorOnRunner<?>, Class<?>>groupingBy(r -> r.getEventClass().get()));
		this.typeLiteralProcessors = processorOnRunners.stream()
			.filter(r -> r.getTypeLiteral().isPresent())
			.collect(Collectors.<EventProcessorOnRunner<?>, TypeLiteral<?>>groupingBy(r -> r.getTypeLiteral().get()));
		this.remoteProcessors = remoteEventProcessorOnRunners.stream()
			.collect(Collectors.<RemoteEventProcessorOnRunner<?>, Class<?>>groupingBy(RemoteEventProcessorOnRunner::getEventClass));
	}

	@Override
	public void start() {
		// No-op
	}

	private <T> void execute(BFTNode sender, T event, TypeLiteral<?> msgType) {
		Class<T> eventClass = (Class<T>) event.getClass();

		if (sender.equals(self)) {
			if (msgType != null) {
				var eventProcessors = typeLiteralProcessors.get(msgType);
				eventProcessors.forEach(p -> p.getProcessor(eventClass).ifPresent(r -> r.process(event)));
			} else {
				List<EventProcessorOnRunner<?>> eventProcessors = processors.get(eventClass);
				eventProcessors.forEach(p -> p.getProcessor(eventClass).ifPresent(r -> r.process(event)));
			}
		} else {
			List<RemoteEventProcessorOnRunner<?>> eventProcessors = remoteProcessors.get(eventClass);
			eventProcessors.forEach(p -> p.getProcessor(eventClass).ifPresent(r -> r.process(sender, event)));
		}
	}

	@Override
	public void handleMessage(BFTNode origin, Object message, TypeLiteral<?> msgType) {
	    this.execute(origin, message, msgType);
	}
}
