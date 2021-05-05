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
import com.radixdlt.environment.StartProcessorOnRunner;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Set;

/**
 * Consensus only (no epochs) deterministic consensus processor
 */
public class DeterministicConsensusProcessor implements DeterministicMessageProcessor {
	private final BFTNode self;
	private final Set<StartProcessorOnRunner> startProcessors;
	private final Set<EventProcessorOnRunner<?>> processorOnRunners;
	private final Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners;

	@Inject
	public DeterministicConsensusProcessor(
		@Self BFTNode self,
		Set<StartProcessorOnRunner> startProcessors,
		Set<EventProcessorOnRunner<?>> processorOnRunners,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners
	) {
		this.self = Objects.requireNonNull(self);
		this.startProcessors = Objects.requireNonNull(startProcessors);
		this.processorOnRunners = Objects.requireNonNull(processorOnRunners);
		this.remoteProcessorOnRunners = Objects.requireNonNull(remoteProcessorOnRunners);
	}

	@Override
	public void start() {
		startProcessors.forEach(p -> p.getProcessor().start());
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean tryExecute(T event, TypeLiteral<?> msgType, EventProcessorOnRunner<?> processor) {
		if (msgType != null) {
			var typeLiteral = (TypeLiteral<T>) msgType;
			final var maybeProcessor = processor.getProcessor(typeLiteral);
			maybeProcessor.ifPresent(p -> p.process(event));
			return maybeProcessor.isPresent();
		} else {
			final var eventClass = (Class<T>) event.getClass();
			final var maybeProcessor = processor.getProcessor(eventClass);
			maybeProcessor.ifPresent(p -> p.process(event));
			return maybeProcessor.isPresent();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean tryExecute(BFTNode origin, T event, RemoteEventProcessorOnRunner<?> processor) {
		final var eventClass = (Class<T>) event.getClass();
		final var maybeProcessor = processor.getProcessor(eventClass);
		maybeProcessor.ifPresent(p -> p.process(origin, event));
		return maybeProcessor.isPresent();
	}

	@Override
	public void handleMessage(BFTNode origin, Object message, TypeLiteral<?> msgType) {
		boolean messageHandled = false;
		if (Objects.equals(self, origin)) {
			for (EventProcessorOnRunner<?> p : processorOnRunners) {
				messageHandled = tryExecute(message, msgType, p) || messageHandled;
			}
		} else {
			for (RemoteEventProcessorOnRunner<?> p : remoteProcessorOnRunners) {
				messageHandled = tryExecute(origin, message, p) || messageHandled;
			}
		}
	}
}
