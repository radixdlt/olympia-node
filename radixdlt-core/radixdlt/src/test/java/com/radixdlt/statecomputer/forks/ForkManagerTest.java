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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ForkManagerTest {

	@Test
	@Ignore // TODO(luk): fixme
	public void fork_manager_should_correctly_manage_forks() {
		final var fork3Proof = proofAtEpoch(10L);

		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), null, 1L);
		final var fork3 = new CandidateForkConfig("fork3", HashCode.fromInt(3), null, sameProof(2L, fork3Proof));

		final var forkManager = ForkManager.create(Set.of(fork1, fork2, fork3));

		assertEquals(fork1, forkManager.genesisFork());
		assertEquals(fork3, forkManager.latestKnownFork());
		assertEquals(fork1, forkManager.getByHash(fork1.getHash()).get());
		assertEquals(fork2, forkManager.getByHash(fork2.getHash()).get());
		assertEquals(fork3, forkManager.getByHash(fork3.getHash()).get());

		// if current fork is 1, then should only return 2
		assertEquals(fork2, forkManager.findNextForkConfig(fork1, null, proofAtEpoch(0L)).get());
		assertTrue(forkManager.findNextForkConfig(fork1, null, fork3Proof).isEmpty());

		// if current fork is 2, the next can only be 3
		assertTrue(forkManager.findNextForkConfig(fork2, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork2, null, proofAtEpoch(1L)).isEmpty());
		assertEquals(fork3, forkManager.findNextForkConfig(fork2, null, fork3Proof).get());

		// if current fork is 3 then shouldn't return any else
		assertTrue(forkManager.findNextForkConfig(fork3, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, proofAtEpoch(1L)).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, fork3Proof).isEmpty());
	}

	@Test
	public void should_correctly_parse_forks_set() {
		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), null, 5L);
		final var fork3 = new FixedEpochForkConfig("fork3", HashCode.fromInt(3), null, 6L);
		final var fork4 = new FixedEpochForkConfig("fork4", HashCode.fromInt(4), null, 7L);
		final var fork5 = new FixedEpochForkConfig("fork5", HashCode.fromInt(5), null, 10L);

		assertEquals(
			ImmutableList.of(fork1, fork2, fork3, fork4, fork5),
			ForkManager.create(Set.of(fork1, fork2, fork3, fork4, fork5)).forkConfigs()
		);
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
