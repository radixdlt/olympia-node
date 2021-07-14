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

import com.google.common.hash.HashCode;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import com.radixdlt.store.EngineStore;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public final class ForkVotesVerifierTest {

	@Test
	public void should_update_the_metadata_with_next_fork_hash_and() {
		final var baseVerifier = (BatchVerifier<LedgerAndBFTProof>) rmock(BatchVerifier.class);
		final var forks = mock(Forks.class);
		final var forkVotesVerifier = new ForkVotesVerifier(baseVerifier, forks);
		final var proof = mock(LedgerProof.class);
		final var currentForkHash = HashCode.fromInt(2);
		final var inputMetadata = LedgerAndBFTProof.create(proof, null, currentForkHash);
		final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
		final var txns = List.<REProcessedTxn>of();

		when(baseVerifier.processMetadata(inputMetadata, engineStore, txns)).thenReturn(inputMetadata);

		final var newValidatorSet = mock(BFTValidatorSet.class);
		when(proof.getNextValidatorSet()).thenReturn(Optional.of(newValidatorSet));

		final var nextFork = mock(ForkConfig.class);
		when(nextFork.hash()).thenReturn(HashCode.fromInt(1));
		when(forks.findNextForkConfig(any())).thenReturn(Optional.of(nextFork));

		final var currentFork = mock(ForkConfig.class);
		when(currentFork.engineRules()).thenReturn(RERulesVersion.OLYMPIA_V1.create(RERulesConfig.testingDefault()));
		when(forks.getByHash(currentForkHash)).thenReturn(Optional.of(currentFork));
		when(engineStore.openIndexedCursor(any())).thenReturn(CloseableCursor.empty());
		final var result = forkVotesVerifier.processMetadata(inputMetadata, engineStore, txns);

		assertEquals(nextFork.hash(), result.getNextForkHash().get());
	}

	@Test
	public void should_not_allow_base_verifier_to_modify_metadata() {
		final var baseVerifier = (BatchVerifier<LedgerAndBFTProof>) rmock(BatchVerifier.class);
		final var forks = mock(Forks.class);
		final var forkVotesVerifier = new ForkVotesVerifier(baseVerifier, forks);
		final var proof = mock(LedgerProof.class);
		final var inputMetadata = LedgerAndBFTProof.create(proof, null, HashCode.fromInt(2));
		final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
		final var txns = List.<REProcessedTxn>of();

		final var newMetadata = LedgerAndBFTProof.create(proof, null, HashCode.fromInt(3));
		when(baseVerifier.processMetadata(inputMetadata, engineStore, txns)).thenReturn(newMetadata);

		assertThrows(IllegalStateException.class, () -> forkVotesVerifier.processMetadata(inputMetadata, engineStore, txns));
	}
}
