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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.InvalidProposedTxn;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

/**
 * Processor of consensus events which gets executed one at a time
 */
@NotThreadSafe
public final class DeterministicEpochsConsensusProcessor implements DeterministicMessageProcessor {
	private final EpochManager epochManager;
	private final Map<Class<?>, EventProcessor<Object>>	eventProcessors;
	private final Map<Class<?>, RemoteEventProcessor<Object>> remoteEventProcessors;
	private final Set<EventProcessorOnRunner<?>> processorOnRunners;
	private final Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners;

	@Inject
	public DeterministicEpochsConsensusProcessor(
		EpochManager epochManager,
		EventProcessor<VertexRequestTimeout> vertexRequestTimeoutEventProcessor,
		EventProcessor<BFTRebuildUpdate> rebuildUpdateEventProcessor,
		EventProcessor<BFTInsertUpdate> bftUpdateProcessor,
		Set<EventProcessor<BFTHighQCUpdate>> bftHighQcUpdateProcessors,
		RemoteEventProcessor<GetVerticesRequest> verticesRequestProcessor,
		RemoteEventProcessor<GetVerticesResponse> verticesResponseProcessor,
		EventProcessor<EpochViewUpdate> epochViewUpdateEventProcessor,
		EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor,
		Set<EventProcessorOnRunner<?>> processorOnRunners,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners
	) {
		this.epochManager = Objects.requireNonNull(epochManager);
		this.processorOnRunners = Objects.requireNonNull(processorOnRunners);
		this.remoteProcessorOnRunners = Objects.requireNonNull(remoteProcessorOnRunners);

		ImmutableMap.Builder<Class<?>, EventProcessor<Object>> processorsBuilder = ImmutableMap.builder();
		// TODO: allow randomization in processing order for a given message
		processorsBuilder.put(VertexRequestTimeout.class, e -> vertexRequestTimeoutEventProcessor.process((VertexRequestTimeout) e));
		processorsBuilder.put(BFTInsertUpdate.class, e -> bftUpdateProcessor.process((BFTInsertUpdate) e));
		processorsBuilder.put(BFTRebuildUpdate.class, e -> rebuildUpdateEventProcessor.process((BFTRebuildUpdate) e));
		processorsBuilder.put(BFTHighQCUpdate.class, e -> bftHighQcUpdateProcessors.forEach(p -> p.process((BFTHighQCUpdate) e)));
		processorsBuilder.put(EpochViewUpdate.class, e -> epochViewUpdateEventProcessor.process((EpochViewUpdate)  e));
		processorsBuilder.put(EpochsLedgerUpdate.class, e -> epochsLedgerUpdateEventProcessor.process((EpochsLedgerUpdate)  e));
		this.eventProcessors = processorsBuilder.build();

		ImmutableMap.Builder<Class<?>, RemoteEventProcessor<Object>> remoteProcessorsBuilder = ImmutableMap.builder();
		remoteProcessorsBuilder.put(
			GetVerticesRequest.class,
			(node, event) -> verticesRequestProcessor.process(node, (GetVerticesRequest) event)
		);
		remoteProcessorsBuilder.put(
			GetVerticesResponse.class,
			(node, event) -> verticesResponseProcessor.process(node, (GetVerticesResponse) event)
		);
		remoteEventProcessors = remoteProcessorsBuilder.build();
	}

	@Override
	public void start() {
		epochManager.start();
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean tryExecute(T event, EventProcessorOnRunner<?> processor) {
		final var eventClass = (Class<T>) event.getClass();
		final var maybeProcessor = processor.getProcessor(eventClass);
		maybeProcessor.ifPresent(p -> p.process(event));
		return maybeProcessor.isPresent();
	}

	@SuppressWarnings("unchecked")
	private static <T> boolean tryExecute(BFTNode origin, T event, RemoteEventProcessorOnRunner<?> processor) {
		final var eventClass = (Class<T>) event.getClass();
		final var maybeProcessor = processor.getProcessor(eventClass);
		maybeProcessor.ifPresent(p -> p.process(origin, event));
		return maybeProcessor.isPresent();
	}

	@Override
	public void handleMessage(BFTNode origin, Object message) {
		if (message instanceof ViewUpdate || message instanceof ScheduledLocalTimeout) {
			// FIXME: Should remove this message type but required due to guice dependency graph
			// FIXME: Should be fixable once an Epoch Environment is implemented
			return;
		} else if (message instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) message);
		} else if (message instanceof Epoched) {
			Epoched<?> epoched = (Epoched<?>) message;
			Object epochedMessage = epoched.event();
			if (epochedMessage instanceof ScheduledLocalTimeout) {
				@SuppressWarnings("unchecked")
				Epoched<ScheduledLocalTimeout> epochTimeout = (Epoched<ScheduledLocalTimeout>) message;
				this.epochManager.processLocalTimeout(epochTimeout);
			} else {
				throw new IllegalArgumentException("Unknown epoch message type: " + epochedMessage.getClass().getName());
			}
		} else if (message instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof LedgerUpdate) {
			// Don't need to process
		} else if (message instanceof AtomsCommittedToLedger) {
			// Don't need to process
		} else if (message instanceof MempoolAddFailure) {
			// Don't need to process
		} else if (message instanceof AtomsRemovedFromMempool) {
			// Don't need to process
		} else if (message instanceof InvalidProposedTxn) {
			// Don't need to process
		} else {
			boolean messageHandled = false;

			for (EventProcessorOnRunner<?> p : processorOnRunners) {
				messageHandled = tryExecute(message, p) || messageHandled;
			}

			for (RemoteEventProcessorOnRunner<?> p : remoteProcessorOnRunners) {
				messageHandled = tryExecute(origin, message, p) || messageHandled;
			}

			EventProcessor<Object> processor = eventProcessors.get(message.getClass());
			if (processor != null) {
				processor.process(message);
				messageHandled = true;
			}

			RemoteEventProcessor<Object> remoteEventProcessor = remoteEventProcessors.get(message.getClass());
			if (remoteEventProcessor != null) {
				remoteEventProcessor.process(origin, message);
				messageHandled = true;
			}

			if (!messageHandled) {
				throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
			}
		}
	}
}
