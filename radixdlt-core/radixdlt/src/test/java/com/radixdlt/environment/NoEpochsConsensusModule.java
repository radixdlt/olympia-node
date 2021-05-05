/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.environment;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.ledger.LedgerUpdate;

public class NoEpochsConsensusModule extends AbstractModule {
	@Override
	public void configure() {
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(ScheduledLocalTimeout.class);
		eventBinder.addBinding().toInstance(VertexRequestTimeout.class);
		eventBinder.addBinding().toInstance(ViewUpdate.class);
		eventBinder.addBinding().toInstance(LedgerUpdate.class);
	}

	@ProvidesIntoSet
	private StartProcessorOnRunner startProcessor(BFTEventProcessor processor) {
		return new StartProcessorOnRunner(
			Runners.CONSENSUS,
			processor::start
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> proposalProcessor(BFTEventProcessor processor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Proposal.class,
			processor::processProposal
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> remoteProposalProcessor(BFTEventProcessor processor) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Proposal.class,
			(node, proposal) -> processor.processProposal(proposal)
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> voteProcessor(BFTEventProcessor processor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Vote.class,
			processor::processVote
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> remoteVoteProcessor(BFTEventProcessor processor) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			Vote.class,
			(node, vote) -> processor.processVote(vote)
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> timeoutProcessor(BFTEventProcessor processor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			ScheduledLocalTimeout.class,
			processor::processLocalTimeout
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> bftSyncTimeoutProcessor(BFTSync bftSync) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			VertexRequestTimeout.class,
			bftSync.vertexRequestTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> viewUpdateProcessor(BFTEventProcessor processor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			ViewUpdate.class,
			processor::processViewUpdate
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> bftSyncResponseProcessor(BFTSync bftSync) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesResponse.class,
			bftSync.responseProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> bftSyncErrorResponseProcessor(BFTSync bftSync) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesErrorResponse.class,
			bftSync.errorResponseProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> bftSyncRequestProcessor(VertexStoreBFTSyncRequestProcessor processor) {
		return new RemoteEventProcessorOnRunner<>(
			Runners.CONSENSUS,
			GetVerticesRequest.class,
			processor
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> bftRebuildUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			BFTRebuildUpdate.class,
			eventProcessor::processBFTRebuildUpdate
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> bftUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			BFTInsertUpdate.class,
			eventProcessor::processBFTUpdate
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> bftSync(BFTSync bftSync) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			BFTInsertUpdate.class,
			bftSync::processBFTUpdate
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> baseLedgerUpdateEventProcessor(BFTSync bftSync) {
		return new EventProcessorOnRunner<>(
			Runners.CONSENSUS,
			LedgerUpdate.class,
			bftSync.baseLedgerUpdateEventProcessor()
		);
	}
}
