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

import com.google.inject.Inject;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.BatchedChecker;
import com.radixdlt.ledger.ByzantineQuorumException;

public final class EpochChecker implements BatchedChecker<LedgerProof> {
	private final ValidatorSetBuilder validatorSetBuilder;

	@Inject
	public EpochChecker(ValidatorSetBuilder validatorSetBuilder) {
		this.validatorSetBuilder = validatorSetBuilder;
	}

	@Override
	public PerStateChangeChecker<LedgerProof> newChecker(ComputedState computedState) {
		return new PerEpochChecker(computedState);
	}

	private class PerEpochChecker implements PerStateChangeChecker<LedgerProof> {
		private long currentEpoch;
		private boolean epochChangeFlag = false;

		private PerEpochChecker(ComputedState initState) {
			this.currentEpoch = initState.get(SystemParticle.class).getEpoch();
		}

		@Override
		public void test(ComputedState computedState) {
			if (epochChangeFlag) {
				throw new IllegalStateException();
			}

			var systemParticle = computedState.get(SystemParticle.class);
			long nextEpoch = systemParticle.getEpoch();
			if (nextEpoch > currentEpoch) {
				epochChangeFlag = true;
			}
		}

		@Override
		public void testMetadata(LedgerProof metadata, ComputedState computedState) {
			// Verify that output of radix engine and signed output match
			// TODO: Always follow radix engine as its a deeper source of truth and just mark validator
			// TODO: set as malicious (RPNV1-633)
			if (epochChangeFlag) {
				if (metadata == null) {
					throw new IllegalStateException();
				}
				final var reNextValidatorSet = validatorSetBuilder.buildValidatorSet(
					computedState.get(RegisteredValidators.class),
					computedState.get(Stakes.class)
				);
				final var signedValidatorSet = metadata.getNextValidatorSet()
					.orElseThrow(() -> new ByzantineQuorumException("RE has changed epochs but proofs don't show."));
				if (!signedValidatorSet.equals(reNextValidatorSet)) {
					throw new ByzantineQuorumException("RE validator set does not agree with signed validator set");
				}
			} else {
				if (metadata != null) {
					metadata.getNextValidatorSet().ifPresent(vset -> {
						throw new IllegalStateException();
					});
				}
			}
		}
	}
}
