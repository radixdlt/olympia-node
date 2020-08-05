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
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.RTTStatistics;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * Processes and reduces BFT events to the BFT state based on core
 * BFT validation logic, any messages which must be sent to other nodes
 * are then forwarded to the BFT sender.
 */
public final class BFTEventReducer implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	/**
	 * Sender of messages to other nodes in the BFT
	 */
	public interface BFTEventSender {

		/**
		 * Broadcast a proposal message to all validators in the network
		 * @param proposal the proposal to broadcast
		 * @param nodes the nodes to broadcast to
		 */
		void broadcastProposal(Proposal proposal, Set<BFTNode> nodes);

		/**
		 * Send a new-view message to a given validator
		 * @param newView the new-view message
		 * @param newViewLeader the validator the message gets sent to
		 */
		void sendNewView(NewView newView, BFTNode newViewLeader);

		/**
		 * Send a vote message to a given validator
		 * @param vote the vote message
		 * @param leader the validator the message gets sent to
		 */
		void sendVote(Vote vote, BFTNode leader);
	}

	/**
	 * Sender of information regarding the BFT
	 */
	public interface BFTInfoSender {

		/**
		 * Signify that the bft node is on a new view
		 * @param view the view the bft node has changed to
		 */
		void sendCurrentView(View view);

		/**
		 * Signify that a timeout was processed by this bft node
		 * @param view the view of the timeout
		 * @param leader the leader of the view which timed out
		 */
		void sendTimeoutProcessed(View view, BFTNode leader);
	}

	private final BFTNode self;
	private final VertexStore vertexStore;
	private final PendingVotes pendingVotes;
	private final ProposalGenerator proposalGenerator;
	private final BFTEventSender sender;
	private final EndOfEpochSender endOfEpochSender;
	private final Pacemaker pacemaker;
	private final ProposerElection proposerElection;
	private final SafetyRules safetyRules;
	private final BFTValidatorSet validatorSet;
	private final SystemCounters counters;
	private final TimeSupplier timeSupplier;
	private final Map<Hash, QuorumCertificate> unsyncedQCs = new HashMap<>();
	private final BFTInfoSender infoSender;
	private final RTTStatistics rttStatistics = new RTTStatistics();
	private boolean synchedLog = false;

	public interface EndOfEpochSender {
		void sendEndOfEpoch(VertexMetadata vertexMetadata);
	}

	public BFTEventReducer(
		BFTNode self,
		ProposalGenerator proposalGenerator,
		BFTEventSender sender,
		EndOfEpochSender endOfEpochSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		PendingVotes pendingVotes,
		ProposerElection proposerElection,
		BFTValidatorSet validatorSet,
		SystemCounters counters,
		BFTInfoSender infoSender,
		TimeSupplier timeSupplier
	) {
		this.self = Objects.requireNonNull(self);
		this.proposalGenerator = Objects.requireNonNull(proposalGenerator);
		this.sender = Objects.requireNonNull(sender);
		this.endOfEpochSender = Objects.requireNonNull(endOfEpochSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.counters = Objects.requireNonNull(counters);
		this.infoSender = Objects.requireNonNull(infoSender);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
	}

	// Hotstuff's Event-Driven OnNextSyncView
	private void proceedToView(View nextView) {
		NewView newView = safetyRules.signNewView(nextView, this.vertexStore.getHighestQC(), this.vertexStore.getHighestCommittedQC());
		BFTNode nextLeader = this.proposerElection.getProposer(nextView);
		log.trace("{}: Sending NEW_VIEW to {}: {}", this.self::getSimpleName, nextLeader::getSimpleName, () ->  newView);
		this.sender.sendNewView(newView, nextLeader);
		this.infoSender.sendCurrentView(nextView);
	}

	private Optional<VertexMetadata> processQC(QuorumCertificate qc) {
		// commit any newly committable vertices
		Optional<VertexMetadata> commitMetaDataMaybe = this.safetyRules.process(qc);
		commitMetaDataMaybe.ifPresent(commitMetaData -> {
			vertexStore.commitVertex(commitMetaData).ifPresent(vertex -> {
				log.trace("{}: Committed vertex: {}", this.self::getSimpleName, () -> vertex);
			});
		});

		// proceed to next view if pacemaker feels like it
		// TODO: should we proceed even if end of epoch?
		this.pacemaker.processQC(qc.getView())
			.ifPresent(this::proceedToView);

		return commitMetaDataMaybe;
	}

	@Override
	public void processLocalSync(Hash vertexId) {
		vertexStore.processLocalSync(vertexId);

		QuorumCertificate qc = unsyncedQCs.remove(vertexId);
		if (qc != null) {
			if (vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), null)) {
				processQC(qc);
				log.trace("{}: LOCAL_SYNC: processed QC: {}", this.self::getSimpleName, () ->  qc);
			} else {
				unsyncedQCs.put(qc.getProposed().getId(), qc);
			}
		}
	}

	@Override
	public void processVote(Vote vote) {
		updateRttStatistics(vote);
		log.trace("{}: VOTE: Processing {}", this.self::getSimpleName, () -> vote);
		// accumulate votes into QCs in store
		this.pendingVotes.insertVote(vote, this.validatorSet).ifPresent(qc -> {
			log.trace("{}: VOTE: Formed QC: {}", this.self::getSimpleName, () -> qc);
			if (vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), vote.getAuthor())) {
				if (!synchedLog) {
					log.debug("{}: VOTE: QC Synced: {}", this.self::getSimpleName, () -> qc);
					synchedLog = true;
				}
				processQC(qc).ifPresent(commitMetaData -> {
					// TODO: should this be sent by everyone and not just the constructor of the proof?
					if (commitMetaData.isEndOfEpoch()) {
						this.endOfEpochSender.sendEndOfEpoch(commitMetaData);
					}
				});
			} else {
				if (synchedLog) {
					log.debug("{}: VOTE: QC Not synced: {}", this.self::getSimpleName, () -> qc);
					synchedLog = false;
				}
				unsyncedQCs.put(qc.getProposed().getId(), qc);
			}
		});
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Processing {}", this.self::getSimpleName, () -> newView);
		processQC(newView.getQC());
		this.pacemaker.processNewView(newView, validatorSet).ifPresent(view -> {
			// Hotstuff's Event-Driven OnBeat
			final Vertex proposedVertex = proposalGenerator.generateProposal(view);
			final Proposal proposal = safetyRules.signProposal(proposedVertex, this.vertexStore.getHighestCommittedQC(), System.nanoTime());
			log.trace("{}: Broadcasting PROPOSAL: {}", this.self::getSimpleName, () -> proposal);
			Set<BFTNode> nodes = validatorSet.getValidators().stream().map(BFTValidator::getNode).collect(Collectors.toSet());
			this.counters.increment(CounterType.BFT_PROPOSALS_MADE);
			this.sender.broadcastProposal(proposal, nodes);
		});
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Processing {}", this.self::getSimpleName, () -> proposal);
		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();

		processQC(proposedVertex.getQC());

		final View updatedView = this.pacemaker.getCurrentView();
		if (proposedVertexView.compareTo(updatedView) != 0) {
			log.trace("{}: PROPOSAL: Ignoring view {} Current is: {}", this.self::getSimpleName, () -> proposedVertexView, () -> updatedView);
			return;
		}

		final VertexMetadata vertexMetadata;
		try {
			vertexMetadata = vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			counters.increment(CounterType.BFT_REJECTED);

			log.warn("{} PROPOSAL: Rejected. Reason: {}", this.self::getSimpleName, e::getMessage);
			return;
		}

		final BFTNode currentLeader = this.proposerElection.getProposer(updatedView);
		try {
			final Vote vote = safetyRules.voteFor(proposedVertex, vertexMetadata, this.timeSupplier.currentTime(), proposal.getPayload());
			log.trace("{}: PROPOSAL: Sending VOTE to {}: {}", this.self::getSimpleName, currentLeader::getSimpleName, () -> vote);
			sender.sendVote(vote, currentLeader);
		} catch (SafetyViolationException e) {
			log.error(() -> new FormattedMessage("{}: PROPOSAL: Rejected {}", this.self.getSimpleName(), proposedVertex), e);
		}

		// If not currently leader or next leader, Proceed to next view
		if (!Objects.equals(currentLeader, this.self)) {
			final BFTNode nextLeader = this.proposerElection.getProposer(updatedView.next());
			if (!Objects.equals(nextLeader, this.self)) {

				// TODO: should not call processQC
				this.pacemaker.processQC(updatedView).ifPresent(this::proceedToView);
			}
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		log.trace("{}: LOCAL_TIMEOUT: Processing {}", this.self::getSimpleName, () -> view);

		// proceed to next view if pacemaker feels like it
		Optional<View> nextView = this.pacemaker.processLocalTimeout(view);
		if (nextView.isPresent()) {
			log.warn("{}: LOCAL_TIMEOUT: Processed View {} Leader: {}",
				this.self::getSimpleName,
				() -> view,
				() -> this.proposerElection.getProposer(view).getSimpleName()
			);

			this.proceedToView(nextView.get());

			infoSender.sendTimeoutProcessed(view, this.proposerElection.getProposer(view));
			counters.increment(CounterType.BFT_TIMEOUT);
		} else {
			log.trace("{}: LOCAL_TIMEOUT: Ignoring {}", this.self::getSimpleName, () -> view);
		}
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.getHighestQC().getView())
			.ifPresent(this::proceedToView);
	}

	private void updateRttStatistics(Vote vote) {
		long durationNanos = System.nanoTime() - vote.getPayload();
		if (durationNanos >= 0L) {
			double durationMicros = durationNanos / 1e3;
			this.rttStatistics.update(durationMicros);
			EnumMap<CounterType, Long> values = new EnumMap<>(CounterType.class);
			values.put(CounterType.BFT_VOTE_RTT_MIN,   Math.round(this.rttStatistics.min()));
			values.put(CounterType.BFT_VOTE_RTT_MAX,   Math.round(this.rttStatistics.max()));
			values.put(CounterType.BFT_VOTE_RTT_MEAN,  Math.round(this.rttStatistics.mean()));
			values.put(CounterType.BFT_VOTE_RTT_SIGMA, Math.round(this.rttStatistics.sigma()));
			values.put(CounterType.BFT_VOTE_RTT_COUNT, this.rttStatistics.count());
			this.counters.setAll(values);
		}
	}
}
