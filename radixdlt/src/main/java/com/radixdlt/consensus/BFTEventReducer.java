/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyViolationException;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.utils.Longs;

import java.util.HashMap;
import java.util.Map;
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

	private final VertexStore vertexStore;
	private final PendingVotes pendingVotes;
	private final ProposalGenerator proposalGenerator;
	private final Mempool mempool;
	private final BFTEventSender sender;
	private final EndOfEpochSender endOfEpochSender;
	private final Pacemaker pacemaker;
	private final ProposerElection proposerElection;
	private final ECKeyPair selfKey; // TODO remove signing/address to separate identity management
	private final SafetyRules safetyRules;
	private final ValidatorSet validatorSet;
	private final SystemCounters counters;
	private final Map<Hash, QuorumCertificate> unsyncedQCs = new HashMap<>();
	private boolean synchedLog = false;

	public interface EndOfEpochSender {
		void sendEndOfEpoch(VertexMetadata vertexMetadata);
	}

	@Inject
	public BFTEventReducer(
		ProposalGenerator proposalGenerator,
		Mempool mempool,
		BFTEventSender sender,
		EndOfEpochSender endOfEpochSender,
		SafetyRules safetyRules,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		PendingVotes pendingVotes,
		ProposerElection proposerElection,
		@Named("self") ECKeyPair selfKey,
		ValidatorSet validatorSet,
		SystemCounters counters
	) {
		this.proposalGenerator = Objects.requireNonNull(proposalGenerator);
		this.mempool = Objects.requireNonNull(mempool);
		this.sender = Objects.requireNonNull(sender);
		this.endOfEpochSender = Objects.requireNonNull(endOfEpochSender);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.counters = Objects.requireNonNull(counters);
	}

	private String getShortName(EUID euid) {
		return euid.toString().substring(0, 6);
	}

	private String getShortName() {
		return getShortName(selfKey.euid());
	}

	// Hotstuff's Event-Driven OnNextSyncView
	private void proceedToView(View nextView) {
		// TODO make signing more robust by including author in signed hash
		ECDSASignature signature = this.selfKey.sign(Hash.hash256(Longs.toByteArray(nextView.number())));
		NewView newView = new NewView(
			selfKey.getPublicKey(),
			nextView,
			this.vertexStore.getHighestQC(),
			this.vertexStore.getHighestCommittedQC(),
			signature
		);
		ECPublicKey nextLeader = this.proposerElection.getProposer(nextView);
		log.trace("{}: Sending NEW_VIEW to {}: {}", this::getShortName, () -> this.getShortName(nextLeader.euid()), () ->  newView);
		this.sender.sendNewView(newView, nextLeader);
		this.counters.set(CounterType.CONSENSUS_VIEW, nextView.number());
	}

	private Optional<VertexMetadata> processQC(QuorumCertificate qc) {
		// commit any newly committable vertices
		Optional<VertexMetadata> commitMetaDataMaybe = this.safetyRules.process(qc);
		commitMetaDataMaybe.ifPresent(commitMetaData -> {
			vertexStore.commitVertex(commitMetaData).ifPresent(vertex -> {
				log.trace("{}: Committed vertex: {}", this::getShortName, () -> vertex);
				final ClientAtom committedAtom = vertex.getAtom();
				if (committedAtom != null) {
					mempool.removeCommittedAtom(committedAtom.getAID());
				}
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
				log.trace("{}: LOCAL_SYNC: processed QC: {}", this::getShortName, () ->  qc);
			} else {
				unsyncedQCs.put(qc.getProposed().getId(), qc);
			}
		}
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("{}: VOTE: Processing {}", this::getShortName, () -> vote);
		// accumulate votes into QCs in store
		this.pendingVotes.insertVote(vote, this.validatorSet).ifPresent(qc -> {
			log.trace("{}: VOTE: Formed QC: {}", this::getShortName, () -> qc);
			if (vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), vote.getAuthor())) {
				if (!synchedLog) {
					log.info("{}: VOTE: QC Synced: {}", this::getShortName, () -> qc);
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
					log.info("{}: VOTE: QC Not synced: {}", this::getShortName, () -> qc);
					synchedLog = false;
				}
				unsyncedQCs.put(qc.getProposed().getId(), qc);
			}
		});
	}

	@Override
	public void processNewView(NewView newView) {
		log.trace("{}: NEW_VIEW: Processing {}", this::getShortName, () -> newView);
		processQC(newView.getQC());
		this.pacemaker.processNewView(newView, validatorSet).ifPresent(view -> {
			// Hotstuff's Event-Driven OnBeat
			final Vertex proposedVertex = proposalGenerator.generateProposal(view);
			final Proposal proposal = safetyRules.signProposal(proposedVertex, this.vertexStore.getHighestCommittedQC());
			log.trace("{}: Broadcasting PROPOSAL: {}", this::getShortName, () -> proposal);
			this.sender.broadcastProposal(proposal, validatorSet.getValidators().stream().map(Validator::nodeKey).collect(Collectors.toSet()));
		});
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("{}: PROPOSAL: Processing {}", this::getShortName, () -> proposal);
		final Vertex proposedVertex = proposal.getVertex();
		final View proposedVertexView = proposedVertex.getView();

		processQC(proposedVertex.getQC());

		final View updatedView = this.pacemaker.getCurrentView();
		if (proposedVertexView.compareTo(updatedView) != 0) {
			log.trace("{}: PROPOSAL: Ignoring view {} Current is: {}", this::getShortName, () -> proposedVertexView, () -> updatedView);
			return;
		}

		if (!proposedVertex.hasDirectParent()) {
			counters.increment(CounterType.CONSENSUS_INDIRECT_PARENT);
		}

		final VertexMetadata vertexMetadata;
		try {
			vertexMetadata = vertexStore.insertVertex(proposedVertex);
		} catch (VertexInsertionException e) {
			counters.increment(CounterType.CONSENSUS_REJECTED);

			log.warn("{} PROPOSAL: Rejected. Reason: {}", this::getShortName, e::getMessage);

			// TODO: Better logic for removal on exception
			final ClientAtom atom = proposedVertex.getAtom();
			if (atom != null) {
				mempool.removeRejectedAtom(atom.getAID());
			}
			return;
		}

		final ECPublicKey currentLeader = this.proposerElection.getProposer(updatedView);
		try {
			final Vote vote = safetyRules.voteFor(proposedVertex, vertexMetadata);
			log.trace("{}: PROPOSAL: Sending VOTE to {}: {}", this::getShortName, () -> this.getShortName(currentLeader.euid()), () -> vote);
			sender.sendVote(vote, currentLeader);
		} catch (SafetyViolationException e) {
			log.error(() -> new FormattedMessage("{}: PROPOSAL: Rejected {}", this.getShortName(), proposedVertex), e);
		}

		// If not currently leader or next leader, Proceed to next view
		if (!Objects.equals(currentLeader, selfKey.getPublicKey())) {
			final ECPublicKey nextLeader = this.proposerElection.getProposer(updatedView.next());
			if (!Objects.equals(nextLeader, selfKey.getPublicKey())) {

				// TODO: should not call processQC
				this.pacemaker.processQC(updatedView).ifPresent(this::proceedToView);
			}
		}
	}

	@Override
	public void processLocalTimeout(View view) {
		log.trace("{}: LOCAL_TIMEOUT: Processing {}", this::getShortName, () -> view);

		// proceed to next view if pacemaker feels like it
		Optional<View> nextView = this.pacemaker.processLocalTimeout(view);
		if (nextView.isPresent()) {
			this.proceedToView(nextView.get());
			log.warn("{}: LOCAL_TIMEOUT: Processed {}", this::getShortName, () -> view);

			counters.set(CounterType.CONSENSUS_TIMEOUT_VIEW, view.number());
			counters.increment(CounterType.CONSENSUS_TIMEOUT);
		} else {
			log.trace("{}: LOCAL_TIMEOUT: Ignoring {}", this::getShortName, () -> view);
		}
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.getHighestQC().getView())
			.ifPresent(this::proceedToView);
	}
}
