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
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.epoch.EpochScheduledLocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
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
		@ProcessWithSyncRunner Set<EventProcessor<EpochsLedgerUpdate>> epochsLedgerUpdateProcessors,
		EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor,
		EventProcessor<LocalGetVerticesRequest> localGetVerticesRequestEventProcessor,
		EventProcessor<SyncInProgress> syncTimeoutProcessor,
		EventProcessor<EpochViewUpdate> epochViewUpdateProcessor,
		Set<EventProcessor<BFTUpdate>> bftUpdateProcessors,
		RemoteEventProcessor<DtoLedgerHeaderAndProof> syncRequestProcessor,
		RemoteEventProcessor<DtoCommandsAndProof> syncResponseProcessor
	) {
		this.epochManager = Objects.requireNonNull(epochManager);

		ImmutableMap.Builder<Class<?>, EventProcessor<Object>> processorsBuilder = ImmutableMap.builder();
		// TODO: allow randomization in processing order for a given message
		processorsBuilder.put(EpochsLedgerUpdate.class, e -> {
			epochManager.processLedgerUpdate((EpochsLedgerUpdate) e);
			epochsLedgerUpdateProcessors.forEach(p -> p.process((EpochsLedgerUpdate) e));
		});
		processorsBuilder.put(EpochViewUpdate.class, e -> epochViewUpdateProcessor.process((EpochViewUpdate) e));
		processorsBuilder.put(LocalSyncRequest.class, e -> localSyncRequestEventProcessor.process((LocalSyncRequest) e));
		processorsBuilder.put(LocalGetVerticesRequest.class, e -> localGetVerticesRequestEventProcessor.process((LocalGetVerticesRequest) e));
		processorsBuilder.put(SyncInProgress.class, e -> syncTimeoutProcessor.process((SyncInProgress) e));
		processorsBuilder.put(BFTUpdate.class, e -> bftUpdateProcessors.forEach(p -> p.process((BFTUpdate) e)));
		this.eventProcessors = processorsBuilder.build();

		ImmutableMap.Builder<Class<?>, RemoteEventProcessor<Object>> remoteProcessorsBuilder = ImmutableMap.builder();
		remoteProcessorsBuilder.put(
			DtoLedgerHeaderAndProof.class,
			(node, event) -> syncRequestProcessor.process(node, (DtoLedgerHeaderAndProof) event)
		);
		remoteProcessorsBuilder.put(
			DtoCommandsAndProof.class,
			(node, event) -> syncResponseProcessor.process(node, (DtoCommandsAndProof) event)
		);
		remoteEventProcessors = remoteProcessorsBuilder.build();
	}

	public void start() {
		epochManager.start();
	}

	@Override
	public void handleMessage(BFTNode origin, Object message) {
		if (message instanceof ViewUpdate) {
			// FIXME: Should remove this message type but required due to guice dependency graph
			// FIXME: Should be fixable once an Epoch Environment is implemented
			return;
		} else if (message instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) message);
		} else if (message instanceof EpochScheduledLocalTimeout) {
			this.epochManager.processLocalTimeout((EpochScheduledLocalTimeout) message);
		} else if (message instanceof GetVerticesRequest) {
			this.epochManager.processGetVerticesRequest((GetVerticesRequest) message);
		} else if (message instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) message);
		} else if (message instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof GetEpochRequest) {
			this.epochManager.processGetEpochRequest((GetEpochRequest) message);
		} else if (message instanceof GetEpochResponse) {
			this.epochManager.processGetEpochResponse((GetEpochResponse) message);
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
