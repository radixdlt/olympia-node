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

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.utils.Triplet;
import com.radixdlt.utils.UInt256;
import org.junit.Test;
import static com.radixdlt.statecomputer.forks.ForksPredicates.stakeVoting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ForksPredicatesTest {

	@Test
	public void test_epoch_predicate() {
		assertFalse(ForksPredicates.atEpoch(100).test(Triplet.of(null, null, proofAtEndOfEpoch(98))));
		// we're at the end of epoch 99, next is 100 so predicate should match
		assertTrue(ForksPredicates.atEpoch(100).test(Triplet.of(null, null, proofAtEndOfEpoch(99))));
		assertTrue(ForksPredicates.atEpoch(100).test(Triplet.of(null, null, proofAtEndOfEpoch(100))));
		assertTrue(ForksPredicates.atEpoch(100).test(Triplet.of(null, null, proofAtEndOfEpoch(101))));
	}

	private LedgerAndBFTProof proofAtEndOfEpoch(long epoch) {
		final var proof  = mock(LedgerAndBFTProof.class);
		final var ledgerProof = mock(LedgerProof.class);
		when(ledgerProof.getNextValidatorSet()).thenReturn(Optional.of(mock(BFTValidatorSet.class)));
		when(proof.getProof()).thenReturn(ledgerProof);
		when(ledgerProof.getEpoch()).thenReturn(epoch);
		return proof;
	}

	@Test
	public void test_stake_voting() {
		final var forkConfig = new ForkConfig("fork1", 0L, null, null);
		final RadixEngine<LedgerAndBFTProof> radixEngine = rmock(RadixEngine.class);
		final var node1 = BFTNode.random();
		final var node2 = BFTNode.random();
		final var node3 = BFTNode.random();
		final var node4 = BFTNode.random();
		final var node5 = BFTNode.random();
		final var node6 = BFTNode.random();

		final var stakedValidators = mock(StakedValidators.class);
		when(radixEngine.getComputedState(StakedValidators.class))
			.thenReturn(stakedValidators);

		final var validatorSet = BFTValidatorSet.from(
			List.of(
				BFTValidator.from(node1, UInt256.ONE),
				BFTValidator.from(node2, UInt256.TWO),
				BFTValidator.from(node3, UInt256.THREE),
				BFTValidator.from(node4, UInt256.ONE),
				BFTValidator.from(node5, UInt256.ONE),
				BFTValidator.from(node6, UInt256.ONE)
			)
		);

		// node2 and node3 have 5/9 power
		when(stakedValidators.getForksVotes()).thenReturn(
			votesOf(forkConfig, node2, node3)
		);
		assertTrue(stakeVoting(0.1).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertTrue(stakeVoting(0.5).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertFalse(stakeVoting(0.6).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));

		// node1, node2, node4, node5 and node6 have 6/9
		when(stakedValidators.getForksVotes()).thenReturn(
			votesOf(forkConfig, node1, node2, node4, node5, node6)
		);
		assertTrue(stakeVoting(0.5).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertTrue(stakeVoting(0.66).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertFalse(stakeVoting(0.69).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));

		// node3 alone has 3/9
		when(stakedValidators.getForksVotes()).thenReturn(
			votesOf(forkConfig, node3)
		);
		assertTrue(stakeVoting(0.32).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertTrue(stakeVoting(0.33).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertFalse(stakeVoting(0.5).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));

		// no votes
		when(stakedValidators.getForksVotes()).thenReturn(
			ImmutableMap.of()
		);
		assertFalse(stakeVoting(0.001).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertFalse(stakeVoting(0.1).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertFalse(stakeVoting(0.5).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet)))); // no votes

		// all votes
		when(stakedValidators.getForksVotes()).thenReturn(
			votesOf(forkConfig, node1, node2, node3, node4, node5, node6)
		);
		assertTrue(stakeVoting(0.5).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
		assertTrue(stakeVoting(1).test(Triplet.of(forkConfig, radixEngine, proofWithValidatorSet(validatorSet))));
	}

	private LedgerAndBFTProof proofWithValidatorSet(BFTValidatorSet validatorSet) {
		final var proof  = mock(LedgerAndBFTProof.class);
		final var ledgerProof = mock(LedgerProof.class);
		when(ledgerProof.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));
		when(proof.getProof()).thenReturn(ledgerProof);
		return proof;
	}

	private ImmutableMap<ECPublicKey, HashCode> votesOf(ForkConfig forkConfig, BFTNode... nodes) {
		return Arrays.stream(nodes)
			.map(node -> Map.entry(node.getKey(), ForkConfig.voteHash(node.getKey(), forkConfig)))
			.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
	}
}
