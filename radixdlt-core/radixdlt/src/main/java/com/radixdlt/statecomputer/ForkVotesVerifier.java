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
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ForkVotesVerifier implements BatchVerifier<LedgerAndBFTProof> {

	private final BatchVerifier<LedgerAndBFTProof> baseVerifier;
	private final ForkManager forkManager;
	private final CommittedReader committedReader;

	public ForkVotesVerifier(
		BatchVerifier<LedgerAndBFTProof> baseVerifier,
	 	ForkManager forkManager,
		CommittedReader committedReader
	) {
		this.baseVerifier = Objects.requireNonNull(baseVerifier);
		this.forkManager = Objects.requireNonNull(forkManager);
		this.committedReader = Objects.requireNonNull(committedReader);
	}

	@Override
	public LedgerAndBFTProof processMetadata(
		LedgerAndBFTProof metadata,
		EngineStore<LedgerAndBFTProof> engineStore,
		List<REProcessedTxn> txns
	) throws MetadataException {
		final var ignoredMetadata = baseVerifier.processMetadata(metadata, engineStore, txns);
		if (ignoredMetadata != metadata) {
			throw new IllegalStateException("Unexpected metadata modification by the baseVerifier");
		}

		final var currentForkConfig = forkManager.getCurrentFork(committedReader.getEpochsForkHashes());

		final var maybeNextForkConfig = metadata.getProof().getNextValidatorSet().isPresent()
			? forkManager.findNextForkConfig(currentForkConfig, engineStore, metadata)
			: Optional.<ForkConfig>empty(); //forks only happen at epoch boundary

		return maybeNextForkConfig
			.map(nextForkConfig -> metadata.withNextForkHash(nextForkConfig.getHash()))
			.orElse(metadata);
	}
}
