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

	@ProvidesIntoSet
	private StartProcessor startProcessor(BFTEventProcessor processor) {
		return processor::start;
	}

	@ProvidesIntoSet
	private EventProcessor<Proposal> proposalProcessor(BFTEventProcessor processor) {
		return processor::processProposal;
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<Proposal> remoteProposalProcessor(BFTEventProcessor processor) {
		return (node, proposal) -> processor.processProposal(proposal);
	}

	@ProvidesIntoSet
	private EventProcessor<Vote> voteProcessor(BFTEventProcessor processor) {
		return processor::processVote;
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<Vote> remoteVoteProcessor(BFTEventProcessor processor) {
		return (node, vote) -> processor.processVote(vote);
	}

	@ProvidesIntoSet
	private EventProcessor<ScheduledLocalTimeout> timeoutProcessor(BFTEventProcessor processor) {
		return processor::processLocalTimeout;
	}

	@ProvidesIntoSet
	public EventProcessor<VertexRequestTimeout> bftSyncTimeoutProcessor(BFTSync bftSync) {
		return bftSync.vertexRequestTimeoutEventProcessor();
	}

	@ProvidesIntoSet
	private EventProcessor<ViewUpdate> viewUpdateProcessor(BFTEventProcessor processor) {
		return processor::processViewUpdate;
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<GetVerticesResponse> bftSyncResponseProcessor(BFTSync bftSync) {
		return bftSync.responseProcessor();
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<GetVerticesErrorResponse> bftSyncErrorResponseProcessor(BFTSync bftSync) {
		return bftSync.errorResponseProcessor();
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<GetVerticesRequest> bftSyncRequestProcessor(VertexStoreBFTSyncRequestProcessor processor) {
		return processor;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTRebuildUpdate> bftRebuildUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return eventProcessor::processBFTRebuildUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTInsertUpdate> bftUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return eventProcessor::processBFTUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTInsertUpdate> bftSync(BFTSync bftSync) {
		return bftSync::processBFTUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<LedgerUpdate> baseLedgerUpdateEventProcessor(BFTSync bftSync) {
		return bftSync.baseLedgerUpdateEventProcessor();
	}
}
