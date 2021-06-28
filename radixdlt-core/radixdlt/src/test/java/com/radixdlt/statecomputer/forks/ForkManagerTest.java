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
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Triplet;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public final class ForkManagerTest {

	@Test
	public void fork_manager_should_correctly_manage_forks() {
		final var fork1Proof = mock(LedgerAndBFTProof.class);
		final var fork2Proof = mock(LedgerAndBFTProof.class);
		final var fork3Proof = mock(LedgerAndBFTProof.class);

		final var fork1 = new ForkConfig("fork1", sameUncommittedProof(fork1Proof), null);
		final var fork2 = new ForkConfig("fork2", sameUncommittedProof(fork2Proof), null);
		final var fork3 = new ForkConfig("fork3", sameUncommittedProof(fork3Proof), null);

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
		final var fork1 = new ForkConfig("fork1", t -> true, null);
		final var fork2 = new ForkConfig("fork2", t -> true, null);
		final var fork3 = new ForkConfig("fork3", t -> true, null);

		final var forkManager = new ForkManager(ImmutableList.of(fork1, fork2, fork3));
		assertEquals(fork3, forkManager.findNextForkConfig(fork1, null, null).get());
	}

	private Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> sameUncommittedProof(
		LedgerAndBFTProof requiredProof
	) {
		return triplet -> triplet.getThird().equals(requiredProof);
	}
}
