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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Objects;

/**
 * Processes and reduces BFT events to the BFT state based on core
 * BFT validation logic, any messages which must be sent to other nodes
 * are then forwarded to the BFT sender.
 */
public final class BFTEventReducer implements BFTEventProcessor {

	private static final Logger log = LogManager.getLogger();

	private final VertexStore vertexStore;
    private final BFTSyncer bftSyncer;
	private final Pacemaker pacemaker;

	public BFTEventReducer(
		Pacemaker pacemaker,
		VertexStore vertexStore,
		BFTSyncer bftSyncer
	) {
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		log.trace("BFTUpdate: Processing {}", update);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: Processing {}", vote);
		// accumulate votes into QCs in store
		this.pacemaker.processVote(vote).ifPresent(qc -> {
			HighQC highQC = HighQC.from(qc, this.vertexStore.highQC().highestCommittedQC());
			// If we are not yet synced, we rely on the syncer to process the QC once received
			this.bftSyncer.syncToQC(highQC, vote.getAuthor());
		});
	}

	@Override
	public void processViewTimeout(ViewTimeout viewTimeout) {
		log.trace("ViewTimeout: Processing {}", viewTimeout);
		this.pacemaker.processViewTimeout(viewTimeout);
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: Processing {}",  proposal);
		this.pacemaker.processProposal(proposal);
	}

	@Override
	public void processLocalTimeout(View view) {
		log.trace("LocalTimeout: Processing {}", view);
		this.pacemaker.processLocalTimeout(view);
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.highQC());
	}
}
