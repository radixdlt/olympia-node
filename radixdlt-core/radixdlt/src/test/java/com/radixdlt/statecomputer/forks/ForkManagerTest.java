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
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import org.junit.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ForkManagerTest {

	@Test
	public void should_fail_when_two_forks_with_the_same_hash() {
		final var committedReader = mock(CommittedReader.class);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(1), null, 1L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			ForkManager.create(committedReader, Set.of(fork1, fork2));
		});

		assertTrue(exception.getMessage().contains("duplicate hashes"));
	}

	@Test
	public void should_fail_when_two_candidate_forks() {
		final var committedReader = mock(CommittedReader.class);

		final var fork1 = new CandidateForkConfig("fork1", HashCode.fromInt(1), null, sameProof(0, null));
		final var fork2 = new CandidateForkConfig("fork2", HashCode.fromInt(2), null, sameProof(0, null));

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			ForkManager.create(committedReader, Set.of(fork1, fork2));
		});

		assertTrue(exception.getMessage().contains("single candidate"));
	}

	@Test
	public void should_fail_when_no_genesis() {
		final var committedReader = mock(CommittedReader.class);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 1L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			ForkManager.create(committedReader, Set.of(fork1));
		});

		assertTrue(exception.getMessage().contains("must start at epoch"));
	}

	@Test
	public void should_fail_when_candidate_epoch_invalid() {
		final var committedReader = mock(CommittedReader.class);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), null, 2L);
		final var fork3 = new CandidateForkConfig("fork3", HashCode.fromInt(3), null, sameProof(2L, null));

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			ForkManager.create(committedReader, Set.of(fork1, fork2, fork3));
		});

		System.out.println(exception.getMessage());
		assertTrue(exception.getMessage().contains("must be greater"));
	}

	@Test
	public void should_fail_when_duplicate_epoch() {
		final var committedReader = mock(CommittedReader.class);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), null, 2L);
		final var fork3 = new FixedEpochForkConfig("fork3", HashCode.fromInt(3), null, 2L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			ForkManager.create(committedReader, Set.of(fork1, fork2, fork3));
		});

		System.out.println(exception.getMessage());
		assertTrue(exception.getMessage().contains("duplicate epoch"));
	}

	@Test
	public void fork_manager_should_correctly_manage_forks() {
		final var fork3Proof = proofAtEpoch(10L);

		final var reRules = new RERules(
			null, null, null, null, BatchVerifier.empty(), View.of(10), OptionalInt.empty(), 10
		);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), reRules, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), reRules, 1L);
		final var fork3 = new CandidateForkConfig("fork3", HashCode.fromInt(3), reRules, sameProof(2L, fork3Proof));

		final var committedReader = mock(CommittedReader.class);
		final var forkManager = ForkManager.create(committedReader, Set.of(fork1, fork2, fork3));

		assertEquals(fork1.getHash(), forkManager.genesisFork().getHash());
		assertEquals(fork3.getHash(), forkManager.latestKnownFork().getHash());
		assertEquals(fork1.getHash(), forkManager.getByHash(fork1.getHash()).get().getHash());
		assertEquals(fork2.getHash(), forkManager.getByHash(fork2.getHash()).get().getHash());
		assertEquals(fork3.getHash(), forkManager.getByHash(fork3.getHash()).get().getHash());

		// if current fork is 1, then should only return 2
		assertEquals(fork2.getHash(), forkManager.findNextForkConfig(fork1, null, proofAtEpoch(0L)).get().getHash());
		assertTrue(forkManager.findNextForkConfig(fork1, null, fork3Proof).isEmpty());

		// if current fork is 2, the next can only be 3
		assertTrue(forkManager.findNextForkConfig(fork2, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork2, null, proofAtEpoch(1L)).isEmpty());
		assertEquals(fork3.getHash(), forkManager.findNextForkConfig(fork2, null, fork3Proof).get().getHash());

		// if current fork is 3 then shouldn't return any else
		assertTrue(forkManager.findNextForkConfig(fork3, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, fork3Proof).isEmpty());
	}

	private LedgerAndBFTProof proofAtEpoch(long epoch) {
		final var ledgerAndBftProof = mock(LedgerAndBFTProof.class);
		final var proof = mock(LedgerProof.class);
		when(proof.getEpoch()).thenReturn(epoch);
		when(ledgerAndBftProof.getProof()).thenReturn(proof);
		return ledgerAndBftProof;
	}

	private CandidateForkPredicate sameProof(long minEpoch, LedgerAndBFTProof requiredProof) {
		return new CandidateForkPredicate() {
			@Override
			public long minEpoch() {
				return minEpoch;
			}

			@Override
			public boolean test(
				CandidateForkConfig forkConfig,
				EngineStore<LedgerAndBFTProof> engineStore,
				REParser reParser,
				LedgerAndBFTProof ledgerAndBFTProof
			) {
				return ledgerAndBFTProof.equals(requiredProof);
			}
		};
	}
}
