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
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Triplet;
import org.junit.Test;
import java.util.function.Predicate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ForkManagerTest {

	@Test
	public void fork_manager_should_correctly_manage_forks() {
		final var fork1Proof = proofAtEpoch(10L);
		final var fork2Proof = proofAtEpoch(10L);
		final var fork3Proof = proofAtEpoch(10L);

		final var fork1 = new ForkConfig("fork1", 0L, sameUncommittedProof(fork1Proof), null);
		final var fork2 = new ForkConfig("fork2", 1L, sameUncommittedProof(fork2Proof), null);
		final var fork3 = new ForkConfig("fork3", 2L, sameUncommittedProof(fork3Proof), null);

		final var forkManager = new ForkManager(ImmutableList.of(fork1, fork2, fork3));

		assertEquals(fork1, forkManager.genesisFork());
		assertEquals(fork3, forkManager.latestKnownFork());
		assertEquals(fork1, forkManager.getByHash(fork1.getHash()).get());
		assertEquals(fork2, forkManager.getByHash(fork2.getHash()).get());
		assertEquals(fork3, forkManager.getByHash(fork3.getHash()).get());

		// if current fork is 1, then should return either 2 or 3 (depending on predicate)
		assertTrue(forkManager.findNextForkConfig(fork1, null, fork1Proof).isEmpty());
		assertEquals(fork2, forkManager.findNextForkConfig(fork1, null, fork2Proof).get());
		assertEquals(fork3, forkManager.findNextForkConfig(fork1, null, fork3Proof).get());

		// if current fork is 2, the next can only be 3
		assertTrue(forkManager.findNextForkConfig(fork2, null, fork1Proof).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork2, null, fork2Proof).isEmpty());
		assertEquals(fork3, forkManager.findNextForkConfig(fork2, null, fork3Proof).get());

		// if current fork is 3 then shouldn't return any else
		assertTrue(forkManager.findNextForkConfig(fork3, null, fork1Proof).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, fork2Proof).isEmpty());
		assertTrue(forkManager.findNextForkConfig(fork3, null, fork3Proof).isEmpty());
	}

	@Test
	public void if_two_predicates_match__then_should_return_latest_fork() {
		final var fork1 = new ForkConfig("fork1", 0L, t -> true, null);
		final var fork2 = new ForkConfig("fork2", 1L, t -> true, null);
		final var fork3 = new ForkConfig("fork3", 2L, t -> true, null);

		final var forkManager = new ForkManager(ImmutableList.of(fork1, fork2, fork3));
		assertEquals(fork3, forkManager.findNextForkConfig(fork1, null, proofAtEpoch(10L)).get());
	}

	@Test
	public void should_not_return_fork_at_later_epoch__even_if_predicates_match() {
		final var fork1 = new ForkConfig("fork1", 0L, t -> true, null);
		final var fork2 = new ForkConfig("fork2", 10L, t -> true, null);

		final var forkManager = new ForkManager(ImmutableList.of(fork1, fork2));
		assertTrue(forkManager.findNextForkConfig(fork1, null, proofAtEpoch(8L)).isEmpty());
	}

	@Test
	public void should_return_next_fork_if_next_epoch_matches() {
		final var fork1 = new ForkConfig("fork1", 0L, t -> true, null);
		final var fork2 = new ForkConfig("fork2", 10L, t -> true, null);

		final var forkManager = new ForkManager(ImmutableList.of(fork1, fork2));
		assertEquals(fork2, forkManager.findNextForkConfig(fork1, null, proofAtEpoch(9L)).get());
	}

	private LedgerAndBFTProof proofAtEpoch(long epoch) {
		final var ledgerAndBftProof = mock(LedgerAndBFTProof.class);
		final var proof = mock(LedgerProof.class);
		when(proof.getEpoch()).thenReturn(epoch);
		when(ledgerAndBftProof.getProof()).thenReturn(proof);
		return ledgerAndBftProof;
	}

	private Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> sameUncommittedProof(
		LedgerAndBFTProof requiredProof
	) {
		return triplet -> triplet.getThird().equals(requiredProof);
	}
}
