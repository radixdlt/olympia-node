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

package com.radixdlt.statecomputer.forks;

import com.google.common.hash.HashCode;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import static com.radixdlt.statecomputer.forks.CandidateForkPredicates.stakeVoting;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class CandidateForkPredicatesTest {

	private final RERules reRules = RERulesVersion.OLYMPIA_V1.create(
		new RERulesConfig(
			FeeTable.create(
				Amount.ofMicroTokens(200),
				Amount.ofTokens(1000)
			),
			OptionalInt.of(50),
			10_000,
			150,
			Amount.ofTokens(100),
			150,
			Amount.ofTokens(10),
			9800,
			100
		)
	);

	private final REParser reParser = reRules.getParser();

	@Test
	public void test_stake_voting() {
		final var forkConfig = new CandidateForkConfig("fork1", HashCode.fromInt(1), null, null);

		final EngineStore<LedgerAndBFTProof> engineStore = rmock(EngineStore.class);
		final var node1 = BFTNode.random();
		final var node2 = BFTNode.random();
		final var node3 = BFTNode.random();
		final var node4 = BFTNode.random();
		final var node5 = BFTNode.random();
		final var node6 = BFTNode.random();

		final var validatorSet = BFTValidatorSet.from(
			List.of(
				BFTValidator.from(node1, UInt256.from(100)),
				BFTValidator.from(node2, UInt256.from(200)),
				BFTValidator.from(node3, UInt256.from(300)),
				BFTValidator.from(node4, UInt256.from(100)),
				BFTValidator.from(node5, UInt256.from(100)),
				BFTValidator.from(node6, UInt256.from(100))
			)
		);

		// node2 and node3 have 5/9 power
		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(forkConfig, node2, node3));

		assertTrue(stakeVoting(0, 1000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertTrue(stakeVoting(0, 5000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertFalse(stakeVoting(0, 6000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));

		// node1, node2, node4, node5 and node6 have 6/9
		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(forkConfig, node1, node2, node4, node5, node6));

		assertTrue(stakeVoting(0, 5000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertTrue(stakeVoting(0, 6600).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertFalse(stakeVoting(0, 6900).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));

		// node3 alone has 3/9
		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(forkConfig, node3));

		assertTrue(stakeVoting(0, 3200).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertTrue(stakeVoting(0, 3300).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertFalse(stakeVoting(0, 5000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));

		// no votes
		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(forkConfig));

		assertFalse(stakeVoting(0, 100).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertFalse(stakeVoting(0, 5000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet))); // no votes

		// all votes
		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(forkConfig, node1, node2, node3, node4, node5, node6));

		assertTrue(stakeVoting(0, 5000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
		assertTrue(stakeVoting(0, 10000).test(forkConfig, engineStore, reParser, proofWithValidatorSet(validatorSet)));
	}

	private CloseableCursor<RawSubstateBytes> votesOf(ForkConfig forkConfig, BFTNode... nodes) {
		return CloseableCursor.of(
			Arrays.stream(nodes)
				.map(n -> voteOf(n, forkConfig.getHash()))
				.toArray(RawSubstateBytes[]::new)
		);
	}

	private RawSubstateBytes voteOf(BFTNode validator, HashCode forkHash) {
		final var pubKey = validator.getKey();
		final var substate = new ValidatorSystemMetadata(pubKey, ForkConfig.voteHash(pubKey, forkHash).asBytes());
		final var serializedSubstate = reRules.getSerialization().serialize(substate);
		return new RawSubstateBytes(new byte[] {}, serializedSubstate);
	}

	private LedgerAndBFTProof proofWithValidatorSet(BFTValidatorSet validatorSet) {
		final var proof  = mock(LedgerAndBFTProof.class);
		final var ledgerProof = mock(LedgerProof.class);
		when(ledgerProof.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));
		when(proof.getProof()).thenReturn(ledgerProof);
		return proof;
	}
}
