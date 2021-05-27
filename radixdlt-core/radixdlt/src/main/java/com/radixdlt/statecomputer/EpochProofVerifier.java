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

package com.radixdlt.statecomputer;

import com.google.common.collect.Sets;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.MetadataException;
import com.radixdlt.ledger.ByzantineQuorumException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validates that the LedgerProof matches the computed state output
 */
public final class EpochProofVerifier implements BatchVerifier<LedgerAndBFTProof> {
	@Override
	public PerStateChangeVerifier<LedgerAndBFTProof> newVerifier(ComputedState computedState) {
		return new PerEpochVerifier(computedState);
	}

	private final class PerEpochVerifier implements PerStateChangeVerifier<LedgerAndBFTProof> {
		private final EpochView epochView;
		private boolean epochChangeFlag = false;

		private PerEpochVerifier(ComputedState initState) {
			this.epochView = initState.get(EpochView.class);
		}

		@Override
		public void test(ComputedState computedState) {
			if (epochChangeFlag) {
				throw new ByzantineQuorumException("Additional commands added to end of epoch.");
			}

			var nextEpochView = computedState.get(EpochView.class);
			if (nextEpochView.getEpoch() > epochView.getEpoch()) {
				epochChangeFlag = true;
			}
		}

		@Override
		public void testMetadata(LedgerAndBFTProof metadata, ComputedState computedState) throws MetadataException {
			// Verify that output of radix engine and signed output match
			// TODO: Always follow radix engine as its a deeper source of truth and just mark validator
			// TODO: set as malicious (RPNV1-633)
			if (epochChangeFlag) {
				if (metadata == null) {
					throw new IllegalStateException();
				}

				final var validatorKeys = computedState.get(CurrentValidators.class).validatorKeys();
				final var reNextValidatorSet = computedState.get(StakedValidators.class).toValidatorSet();
				if (reNextValidatorSet == null) {
					throw new MetadataException("Computed state has no staked validators.");
				}
				final var signedValidatorSet = metadata.getProof().getNextValidatorSet()
					.orElseThrow(() -> new MetadataException("RE has changed epochs but proofs don't show."));

				var stakedKeys = reNextValidatorSet.nodes().stream().map(BFTNode::getKey).collect(Collectors.toSet());
				if (!Objects.equals(stakedKeys, validatorKeys)) {
					throw new MetadataException(
						String.format(
							"Current validators does not agree with staked validators stakedDiff: %s currentDiff: %s",
							Sets.difference(stakedKeys, validatorKeys),
							Sets.difference(validatorKeys, stakedKeys)
						));
				}

				if (!signedValidatorSet.equals(reNextValidatorSet)) {
					throw new MetadataException(
						String.format(
							"RE validator set %s does not agree with signed validator set %s",
							reNextValidatorSet, signedValidatorSet
						));
				}
			} else {
				if (metadata != null) {
					if (metadata.getProof().getNextValidatorSet().isPresent()) {
						throw new MetadataException("RE validator set should not be present.");
					}
				}
			}
		}
	}
}
