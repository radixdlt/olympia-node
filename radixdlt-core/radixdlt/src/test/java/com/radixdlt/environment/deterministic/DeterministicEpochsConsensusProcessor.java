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
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;

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

	@Inject
	public DeterministicEpochsConsensusProcessor(
		EpochManager epochManager,
		EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor,
		@ProcessWithSyncRunner Set<EventProcessor<EpochsLedgerUpdate>> epochsLedgerUpdateProcessors,
		EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor,
		EventProcessor<VertexRequestTimeout> vertexRequestTimeoutEventProcessor,
		EventProcessor<SyncCheckTrigger> syncCheckTriggerProcessor,
		EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutProcessor,
		EventProcessor<SyncRequestTimeout> syncRequestTimeoutProcessor,
		EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor,
		EventProcessor<EpochViewUpdate> epochViewUpdateProcessor,
		EventProcessor<BFTRebuildUpdate> rebuildUpdateEventProcessor,
		EventProcessor<BFTInsertUpdate> bftUpdateProcessor,
		Set<EventProcessor<BFTHighQCUpdate>> bftHighQcUpdateProcessors,
		RemoteEventProcessor<GetVerticesRequest> verticesRequestProcessor,
		RemoteEventProcessor<StatusRequest> statusRequestProcessor,
		RemoteEventProcessor<StatusResponse> statusResponseProcessor,
		RemoteEventProcessor<SyncRequest> syncRequestProcessor,
		RemoteEventProcessor<SyncResponse> syncResponseProcessor
	) {
		this.epochManager = Objects.requireNonNull(epochManager);

		ImmutableMap.Builder<Class<?>, EventProcessor<Object>> processorsBuilder = ImmutableMap.builder();
		// TODO: allow randomization in processing order for a given message
		processorsBuilder.put(EpochsLedgerUpdate.class, e -> {
			epochsLedgerUpdateEventProcessor.process((EpochsLedgerUpdate) e);
			epochsLedgerUpdateProcessors.forEach(p -> p.process((EpochsLedgerUpdate) e));
		});
		processorsBuilder.put(EpochViewUpdate.class, e -> epochViewUpdateProcessor.process((EpochViewUpdate) e));
		processorsBuilder.put(LocalSyncRequest.class, e -> localSyncRequestEventProcessor.process((LocalSyncRequest) e));
		processorsBuilder.put(VertexRequestTimeout.class, e -> vertexRequestTimeoutEventProcessor.process((VertexRequestTimeout) e));
		processorsBuilder.put(SyncCheckTrigger.class, e -> syncCheckTriggerProcessor.process((SyncCheckTrigger) e));
		processorsBuilder.put(
			SyncCheckReceiveStatusTimeout.class,
			e -> syncCheckReceiveStatusTimeoutProcessor.process((SyncCheckReceiveStatusTimeout) e)
		);
		processorsBuilder.put(SyncRequestTimeout.class, e -> syncRequestTimeoutProcessor.process((SyncRequestTimeout) e));
		processorsBuilder.put(SyncLedgerUpdateTimeout.class, e ->
			syncLedgerUpdateTimeoutProcessor.process((SyncLedgerUpdateTimeout) e)
		);
		processorsBuilder.put(BFTInsertUpdate.class, e -> bftUpdateProcessor.process((BFTInsertUpdate) e));
		processorsBuilder.put(BFTRebuildUpdate.class, e -> rebuildUpdateEventProcessor.process((BFTRebuildUpdate) e));
		processorsBuilder.put(BFTHighQCUpdate.class, e -> bftHighQcUpdateProcessors.forEach(p -> p.process((BFTHighQCUpdate) e)));
		this.eventProcessors = processorsBuilder.build();

		ImmutableMap.Builder<Class<?>, RemoteEventProcessor<Object>> remoteProcessorsBuilder = ImmutableMap.builder();
		remoteProcessorsBuilder.put(
			GetVerticesRequest.class,
			(node, event) -> verticesRequestProcessor.process(node, (GetVerticesRequest) event)
		);
		remoteProcessorsBuilder.put(
			StatusRequest.class,
			(node, event) -> statusRequestProcessor.process(node, (StatusRequest) event)
		);
		remoteProcessorsBuilder.put(
			StatusResponse.class,
			(node, event) -> statusResponseProcessor.process(node, (StatusResponse) event)
		);
		remoteProcessorsBuilder.put(
			SyncRequest.class,
			(node, event) -> syncRequestProcessor.process(node, (SyncRequest) event)
		);
		remoteProcessorsBuilder.put(
			SyncResponse.class,
			(node, event) -> syncResponseProcessor.process(node, (SyncResponse) event)
		);
		remoteEventProcessors = remoteProcessorsBuilder.build();
	}

	@Override
	public void start() {
		epochManager.start();
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
		} else if (message instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) message);
		} else if (message instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof GetEpochRequest) {
			this.epochManager.processGetEpochRequest((GetEpochRequest) message);
		} else if (message instanceof GetEpochResponse) {
			this.epochManager.processGetEpochResponse((GetEpochResponse) message);
		} else if (message instanceof LedgerUpdate) {
			// Don't need to process
		} else if (message instanceof AtomCommittedToLedger) {
			// Don't need to process
		} else {

			EventProcessor<Object> processor = eventProcessors.get(message.getClass());
			if (processor != null) {
				processor.process(message);
				return;
			}

			RemoteEventProcessor<Object> remoteEventProcessor = remoteEventProcessors.get(message.getClass());
			if (remoteEventProcessor != null) {
				remoteEventProcessor.process(origin, message);
				return;
			}

			throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
		}
	}
}
