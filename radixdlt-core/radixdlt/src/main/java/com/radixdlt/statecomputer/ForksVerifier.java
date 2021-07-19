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

import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.MetadataException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.EngineStore;
import java.util.List;

public final class ForksVerifier implements BatchVerifier<LedgerAndBFTProof> {

	private final BatchVerifier<LedgerAndBFTProof> baseVerifier;
	private final REParser reParser;
	private final ForkConfig nextFork;

	public ForksVerifier(
		BatchVerifier<LedgerAndBFTProof> baseVerifier,
		REParser reParser,
		ForkConfig nextFork
	) {
		this.baseVerifier = baseVerifier;
		this.reParser = reParser;
		this.nextFork = nextFork;
	}

	@Override
	public LedgerAndBFTProof processMetadata(
		LedgerAndBFTProof metadata,
		EngineStore<LedgerAndBFTProof> engineStore,
		List<REProcessedTxn> txns
	) throws MetadataException {
		final var baseMetadata = baseVerifier.processMetadata(metadata, engineStore, txns);

		if (baseMetadata.getProof().getNextValidatorSet().isPresent() && shouldSwitchToNextFork(baseMetadata)) {
			return baseMetadata.withNextForkHash(nextFork.hash());
		} else {
			return baseMetadata;
		}
	}

	private boolean shouldSwitchToNextFork(LedgerAndBFTProof ledgerAndBFTProof) {
		if (nextFork instanceof FixedEpochForkConfig) {
			final var forkEpoch = ((FixedEpochForkConfig) nextFork).epoch();
			final var nextEpoch = ledgerAndBFTProof.getProof().getEpoch();
			return forkEpoch == nextEpoch;
		} else if (nextFork instanceof CandidateForkConfig) {
			return Forks.testCandidate((CandidateForkConfig) nextFork, reParser, ledgerAndBFTProof);
		} else {
			throw new IllegalStateException();
		}
	}
}
