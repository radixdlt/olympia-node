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

import com.google.inject.Inject;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;

import java.util.Objects;
import java.util.Set;

/**
 * Consensus only (no epochs) deterministic consensus processor
 */
public class DeterministicConsensusProcessor implements DeterministicMessageProcessor {
	private final BFTEventProcessor bftEventProcessor;
	private final BFTSync vertexStoreSync;
	private final Set<RemoteEventProcessor<GetVerticesRequest>> verticesRequestProcessors;
	private final Set<RemoteEventProcessor<GetVerticesResponse>> verticesResponseProcessors;

	private final Set<RemoteEventProcessor<GetVerticesErrorResponse>> bftSyncErrorResponseProcessors;
	private final Set<EventProcessor<BFTHighQCUpdate>> bftHighQCUpdateProcessors;
	private final Set<EventProcessor<BFTInsertUpdate>> bftUpdateProcessors;
	private final Set<EventProcessor<BFTRebuildUpdate>> bftRebuildUpdateProcessors;
	private final Set<EventProcessor<ViewUpdate>> viewUpdateProcessors;
	private final Set<EventProcessor<ScheduledLocalTimeout>> timeoutProcessors;
	private final Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors;

	@Inject
	public DeterministicConsensusProcessor(
		BFTEventProcessor bftEventProcessor,
		BFTSync vertexStoreSync,
		Set<RemoteEventProcessor<GetVerticesRequest>> verticesRequestProcessors,
		Set<RemoteEventProcessor<GetVerticesResponse>> verticesResponseProcessors,
		Set<RemoteEventProcessor<GetVerticesErrorResponse>> bftSyncErrorResponseProcessors,
		Set<EventProcessor<ViewUpdate>> viewUpdateProcessors,
		Set<EventProcessor<BFTInsertUpdate>> bftUpdateProcessors,
		Set<EventProcessor<BFTRebuildUpdate>> bftRebuildUpdateProcessors,
		Set<EventProcessor<BFTHighQCUpdate>> bftHighQCUpdateProcessors,
		Set<EventProcessor<ScheduledLocalTimeout>> timeoutProcessors,
		Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors
	) {
		this.bftEventProcessor = Objects.requireNonNull(bftEventProcessor);
		this.vertexStoreSync = Objects.requireNonNull(vertexStoreSync);
		this.verticesRequestProcessors = Objects.requireNonNull(verticesRequestProcessors);
		this.verticesResponseProcessors = Objects.requireNonNull(verticesResponseProcessors);
		this.bftSyncErrorResponseProcessors = Objects.requireNonNull(bftSyncErrorResponseProcessors);
		this.bftUpdateProcessors = Objects.requireNonNull(bftUpdateProcessors);
		this.bftRebuildUpdateProcessors = Objects.requireNonNull(bftRebuildUpdateProcessors);
		this.bftHighQCUpdateProcessors = Objects.requireNonNull(bftHighQCUpdateProcessors);
		this.viewUpdateProcessors = Objects.requireNonNull(viewUpdateProcessors);
		this.timeoutProcessors = Objects.requireNonNull(timeoutProcessors);
		this.ledgerUpdateProcessors = Objects.requireNonNull(ledgerUpdateProcessors);
	}

	@Override
	public void start() {
		this.bftEventProcessor.start();
	}

	@Override
	public void handleMessage(BFTNode origin, Object message) {
		if (message instanceof ScheduledLocalTimeout) {
			timeoutProcessors.forEach(p -> p.process((ScheduledLocalTimeout) message));
		} else if (message instanceof Proposal) {
			bftEventProcessor.processProposal((Proposal) message);
		} else if (message instanceof Vote) {
			bftEventProcessor.processVote((Vote) message);
		} else if (message instanceof ViewUpdate) {
			viewUpdateProcessors.forEach(p -> p.process((ViewUpdate) message));
		} else if (message instanceof GetVerticesRequest) {
			verticesRequestProcessors.forEach(p -> p.process(origin, (GetVerticesRequest) message));
		} else if (message instanceof GetVerticesResponse) {
			verticesResponseProcessors.forEach(p -> p.process(origin, (GetVerticesResponse) message));
		} else if (message instanceof GetVerticesErrorResponse) {
			bftSyncErrorResponseProcessors.forEach(p -> p.process(origin, (GetVerticesErrorResponse) message));
		} else if (message instanceof BFTHighQCUpdate) {
			bftHighQCUpdateProcessors.forEach(p -> p.process((BFTHighQCUpdate) message));
		} else if (message instanceof BFTInsertUpdate) {
			bftUpdateProcessors.forEach(p -> p.process((BFTInsertUpdate) message));
		} else if (message instanceof BFTRebuildUpdate) {
			bftRebuildUpdateProcessors.forEach(p -> p.process((BFTRebuildUpdate) message));
		} else if (message instanceof LedgerUpdate) {
			ledgerUpdateProcessors.forEach(p -> p.process((LedgerUpdate) message));
		} else if (message instanceof VertexRequestTimeout) {
			vertexStoreSync.vertexRequestTimeoutEventProcessor().process((VertexRequestTimeout) message);
		} else if (message instanceof AtomsCommittedToLedger) {
			// Don't need to process
		} else {
			throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
		}
	}
}
