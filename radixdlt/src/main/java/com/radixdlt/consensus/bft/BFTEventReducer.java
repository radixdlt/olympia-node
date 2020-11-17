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
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;

import com.radixdlt.environment.EventDispatcher;
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
	private final Pacemaker pacemaker;
	private final EventDispatcher<FormedQC> formedQCEventDispatcher;

	public BFTEventReducer(
		Pacemaker pacemaker,
		VertexStore vertexStore,
		EventDispatcher<FormedQC> formedQCEventDispatcher
	) {
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.formedQCEventDispatcher = Objects.requireNonNull(formedQCEventDispatcher);
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		log.trace("BFTUpdate: Processing {}", update);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: Processing {}", vote);
		// accumulate votes into QCs in store
		this.pacemaker.processVote(vote).ifPresent(qc ->
			formedQCEventDispatcher.dispatch(FormedQC.create(qc, vote.getAuthor()))
		);
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
