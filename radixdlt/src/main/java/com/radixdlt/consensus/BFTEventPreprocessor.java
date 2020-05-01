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

package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preprocesses consensus events and ensures that the vertexStore is synced to
 * the correct state before they get forwarded to the actual state reducer.
 *
 * This class should not be updating any part of the BFT Safety state besides
 * the VertexStore.
 */
public final class BFTEventPreprocessor implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTEventProcessor forwardTo;
	private final VertexStore vertexStore;
	private final PacemakerState pacemakerState;
	private final PacemakerRx pacemakerRx;
	private final ProposerElection proposerElection;
	private final SystemCounters counters;
	private final ECPublicKey myKey;

	@Inject
	public BFTEventPreprocessor(
		ECPublicKey myKey,
		BFTEventProcessor forwardTo,
		PacemakerState pacemakerState,
		PacemakerRx pacemakerRx,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		SystemCounters counters
	) {
		this.myKey = Objects.requireNonNull(myKey);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.pacemakerRx = Objects.requireNonNull(pacemakerRx);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.counters = Objects.requireNonNull(counters);
		this.forwardTo = forwardTo;
	}

	private String getShortName() {
		return myKey.euid().toString().substring(0, 6);
	}

	private void sync(QuorumCertificate qc, ECPublicKey node) throws SyncException {
		// sync up to QC if necessary
		try {
			this.vertexStore.syncToQC(qc, node, this.pacemakerRx.timeout(this.pacemakerState.getCurrentView()));
		} catch (SyncException e) {
			counters.increment(CounterType.CONSENSUS_SYNC_EXCEPTION);
			throw e;
		}
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("{}: VOTE: Processing {}", this.getShortName(), vote);

		// only do something if we're actually the leader for the vote
		final View view = vote.getVoteData().getProposed().getView();
		// TODO: currently we don't check view of vote relative to our pacemakerState. This opens
		// TODO: up to dos attacks on calculation of next proposer if ProposerElection is
		// TODO: an expensive operation. Need to figure out a way of mitigating this problem
		// TODO: perhaps through filter views too out of bounds
		if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
			log.warn("{}: VOTE: Ignoring confused vote {} for {}",
				getShortName(), vote.hashCode(), vote.getVoteData().getProposed().getView());
			return;
		}

		forwardTo.processVote(vote);
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Processing: {}", this.getShortName(), newView);

		final View currentView = this.pacemakerState.getCurrentView();
		if (newView.getView().compareTo(currentView) < 0) {
			log.info("{}: NEW_VIEW: Ignoring {} Current is: {}", this.getShortName(), newView.getView(), currentView);
			return;
		}

		// only do something if we're actually the leader for the view
		final View view = newView.getView();
		if (!Objects.equals(proposerElection.getProposer(view), myKey)) {
			log.warn("{}: NEW_VIEW: Got confused new-view {} for view {}", this.getShortName(), newView.hashCode(), newView.getView());
			return;
		}

		try {
			this.sync(newView.getQC(), newView.getAuthor());
		} catch (SyncException e) {
			log.warn("{}: NEW_VIEW: Ignoring new view because unable to sync to QC {}", this.getShortName(), e.getQC(), e.getCause());
			return;
		}

		forwardTo.processNewView(newView);
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Processing {}", this.getShortName(), proposal);

		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();
		final View currentView = this.pacemakerState.getCurrentView();
		if (proposedVertexView.compareTo(currentView) < 0) {
			log.info("{}: PROPOSAL: Ignoring view {} Current is: {}", this.getShortName(), proposedVertexView, currentView);
			return;
		}

		try {
			this.sync(proposedVertex.getQC(), proposal.getAuthor());
		} catch (SyncException e) {
			log.warn("{}: PROPOSAL: Ignoring because unable to sync to QC {}", this.getShortName(), e.getQC(), e.getCause());
			return;
		}

		forwardTo.processProposal(proposal);
	}

	@Override
	public void processLocalTimeout(View view) {
		forwardTo.processLocalTimeout(view);
	}

	@Override
	public void processGetVertexRequest(GetVertexRequest request) {
		forwardTo.processGetVertexRequest(request);
	}

	@Override
	public void start() {
		forwardTo.start();
	}
}
