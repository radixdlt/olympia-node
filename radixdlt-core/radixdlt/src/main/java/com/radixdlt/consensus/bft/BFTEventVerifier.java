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

import com.google.common.hash.HashCode;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.liveness.VoteTimeout;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECDSASignature;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verifies signatures of BFT messages then forwards to the next processor
 */
@SecurityCritical({ SecurityKind.SIG_VERIFY })
public final class BFTEventVerifier implements BFTEventProcessor {
	private static final Logger log = LogManager.getLogger();

	private final BFTValidatorSet validatorSet;
	private final BFTEventProcessor forwardTo;
	private final Hasher hasher;
	private final HashVerifier verifier;

	public BFTEventVerifier(
		BFTValidatorSet validatorSet,
		BFTEventProcessor forwardTo,
		Hasher hasher,
		HashVerifier verifier
	) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.hasher = Objects.requireNonNull(hasher);
		this.verifier = Objects.requireNonNull(verifier);
		this.forwardTo = forwardTo;
	}

	@Override
	public void start() {
		forwardTo.start();
	}

	@Override
	public void processViewUpdate(ViewUpdate viewUpdate) {
		forwardTo.processViewUpdate(viewUpdate);
	}

	@Override
	public void processVote(Vote vote) {
		validAuthor(vote).ifPresent(node -> {
			boolean verifiedVoteData = verifyHash(node, vote.getHashOfData(hasher), vote.getSignature(), vote);
			if (!verifiedVoteData) {
				log.warn("Ignoring invalid vote data {}", vote);
				return;
			}

			boolean verifiedTimeoutData = vote.getTimeoutSignature()
				.map(timeoutSignature -> verify(node, VoteTimeout.of(vote), timeoutSignature, vote))
				.orElse(true);

			if (!verifiedTimeoutData) {
				log.warn("Ignoring invalid timeout data {}", vote);
				return;
			}

			forwardTo.processVote(vote);
		});
	}

	@Override
	public void processProposal(Proposal proposal) {
		validAuthor(proposal).ifPresent(node -> {
			if (verify(node, proposal.getVertex(), proposal.getSignature(), proposal)) {
				forwardTo.processProposal(proposal);
			}
		});
	}

	@Override
	public void processLocalTimeout(ScheduledLocalTimeout localTimeout) {
		forwardTo.processLocalTimeout(localTimeout);
	}

	@Override
	public void processBFTUpdate(BFTInsertUpdate update) {
		forwardTo.processBFTUpdate(update);
	}

	@Override
	public void processBFTRebuildUpdate(BFTRebuildUpdate update) {
		forwardTo.processBFTRebuildUpdate(update);
	}

	private Optional<BFTNode> validAuthor(ConsensusEvent event) {
		BFTNode node = event.getAuthor();
		if (!validatorSet.containsNode(node)) {
			log.warn(
				"CONSENSUS_EVENT: {} from author {} not in validator set {}",
				 event.getClass().getSimpleName(),
				 node,
				 this.validatorSet
			);
			return Optional.empty();
		}
		return Optional.of(node);
	}

	private boolean verifyHash(BFTNode author, HashCode hash, ECDSASignature signature, Object what) {
		boolean verified = this.verifier.verify(author.getKey(), hash, signature);
		if (!verified) {
			log.info("Ignoring invalid signature from {} for {}", author, what);
		}
		return verified;
	}

	private boolean verify(BFTNode author, Object hashable, ECDSASignature signature, Object what) {
		return verifyHash(author, this.hasher.hash(hashable), signature, what);
	}
}
