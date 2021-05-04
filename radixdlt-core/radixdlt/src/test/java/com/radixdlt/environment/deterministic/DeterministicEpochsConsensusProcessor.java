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
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.StartProcessor;
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
	private final BFTNode self;
	private final Map<Class<?>, EventProcessor<Object>>	eventProcessors;
	private final Map<Class<?>, RemoteEventProcessor<Object>> remoteEventProcessors;
	private final Set<StartProcessor> startProcessors;
	private final Set<EventProcessor<Epoched<ScheduledLocalTimeout>>> epochTimeoutProcessors;
	private final Set<EventProcessorOnRunner<?>> processorOnRunners;
	private final Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners;

	@Inject
	public DeterministicEpochsConsensusProcessor(
		@Self BFTNode self,
		Set<StartProcessor> startProcessors,
		Set<EventProcessor<Vote>> localVoteProcessors,
		Set<RemoteEventProcessor<Vote>> remoteVoteProcessors,
		Set<EventProcessor<Proposal>> localProposalProcessors,
		Set<RemoteEventProcessor<Proposal>> remoteProposalProcessors,
		Set<EventProcessor<Epoched<ScheduledLocalTimeout>>> epochTimeoutProcessors,
		Set<RemoteEventProcessor<GetVerticesRequest>> verticesRequestProcessors,
		Set<RemoteEventProcessor<GetVerticesResponse>> verticesResponseProcessors,
		Set<EventProcessor<VertexRequestTimeout>> vertexRequestTimeoutEventProcessors,
		Set<EventProcessor<BFTRebuildUpdate>> rebuildUpdateEventProcessors,
		Set<EventProcessor<BFTInsertUpdate>> bftUpdateProcessors,
		Set<EventProcessor<BFTHighQCUpdate>> bftHighQcUpdateProcessors,
		Set<EventProcessor<EpochViewUpdate>> epochViewUpdateEventProcessors,
		Set<EventProcessor<EpochsLedgerUpdate>> epochsLedgerUpdateEventProcessors,
		Set<EventProcessorOnRunner<?>> processorOnRunners,
		Set<RemoteEventProcessorOnRunner<?>> remoteProcessorOnRunners
	) {
		this.self = Objects.requireNonNull(self);
		this.startProcessors = Objects.requireNonNull(startProcessors);
		this.epochTimeoutProcessors = Objects.requireNonNull(epochTimeoutProcessors);
		this.processorOnRunners = Objects.requireNonNull(processorOnRunners);
		this.remoteProcessorOnRunners = Objects.requireNonNull(remoteProcessorOnRunners);

		ImmutableMap.Builder<Class<?>, EventProcessor<Object>> processorsBuilder = ImmutableMap.builder();
		// TODO: allow randomization in processing order for a given message
		processorsBuilder.put(
			VertexRequestTimeout.class,
			e -> vertexRequestTimeoutEventProcessors.forEach(p -> p.process((VertexRequestTimeout) e))
		);
		processorsBuilder.put(BFTInsertUpdate.class, e -> bftUpdateProcessors.forEach(p -> p.process((BFTInsertUpdate) e)));
		processorsBuilder.put(BFTRebuildUpdate.class, e -> rebuildUpdateEventProcessors.forEach(p -> p.process((BFTRebuildUpdate) e)));
		processorsBuilder.put(
			BFTHighQCUpdate.class,
			e -> bftHighQcUpdateProcessors.forEach(p -> p.process((BFTHighQCUpdate) e))
		);
		processorsBuilder.put(
			EpochViewUpdate.class,
			e -> epochViewUpdateEventProcessors.forEach(p -> p.process((EpochViewUpdate)  e))
		);
		processorsBuilder.put(
			EpochsLedgerUpdate.class,
			e -> epochsLedgerUpdateEventProcessors.forEach(p -> p.process((EpochsLedgerUpdate) e))
		);
		processorsBuilder.put(Vote.class, e -> localVoteProcessors.forEach(p -> p.process((Vote) e)));
		processorsBuilder.put(Proposal.class, e -> localProposalProcessors.forEach(p -> p.process((Proposal) e)));
		this.eventProcessors = processorsBuilder.build();

		ImmutableMap.Builder<Class<?>, RemoteEventProcessor<Object>> remoteProcessorsBuilder = ImmutableMap.builder();
		remoteProcessorsBuilder.put(
			Vote.class,
			(node, event) -> remoteVoteProcessors.forEach(p -> p.process(node, (Vote) event))
		);
		remoteProcessorsBuilder.put(
			Proposal.class,
			(node, event) -> remoteProposalProcessors.forEach(p -> p.process(node, (Proposal) event))
		);
		remoteProcessorsBuilder.put(
			GetVerticesRequest.class,
			(node, event) -> verticesRequestProcessors.forEach(p -> p.process(node, (GetVerticesRequest) event))
		);
		remoteProcessorsBuilder.put(
			GetVerticesResponse.class,
			(node, event) -> verticesResponseProcessors.forEach(p -> p.process(node, (GetVerticesResponse) event))
		);
		remoteEventProcessors = remoteProcessorsBuilder.build();
	}

	@Override
	public void start() {
		startProcessors.forEach(StartProcessor::start);
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
		} else if (message instanceof Epoched) {
			Epoched<?> epoched = (Epoched<?>) message;
			Object epochedMessage = epoched.event();
			if (epochedMessage instanceof ScheduledLocalTimeout) {
				@SuppressWarnings("unchecked")
				Epoched<ScheduledLocalTimeout> epochTimeout = (Epoched<ScheduledLocalTimeout>) message;
				this.epochTimeoutProcessors.forEach(p -> p.process(epochTimeout));
			} else {
				throw new IllegalArgumentException("Unknown epoch message type: " + epochedMessage.getClass().getName());
			}
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
			if (Objects.equals(self, origin)) {
				for (EventProcessorOnRunner<?> p : processorOnRunners) {
					messageHandled = tryExecute(message, p) || messageHandled;
				}

				var processor = eventProcessors.get(message.getClass());
				if (processor != null) {
					processor.process(message);
					messageHandled = true;
				}
			} else {
				var remoteEventProcessor = remoteEventProcessors.get(message.getClass());
				if (remoteEventProcessor != null) {
					remoteEventProcessor.process(origin, message);
					messageHandled = true;
				}

				for (RemoteEventProcessorOnRunner<?> p : remoteProcessorOnRunners) {
					messageHandled = tryExecute(origin, message, p) || messageHandled;
				}
			}

			if (!messageHandled) {
				throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
			}
		}
	}
}
