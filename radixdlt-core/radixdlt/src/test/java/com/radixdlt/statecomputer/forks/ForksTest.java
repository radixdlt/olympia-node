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
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ForksTest {

	@Test
	public void should_fail_when_two_forks_with_the_same_hash() {
		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(1), null, 1L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			Forks.create(Set.of(fork1, fork2));
		});

		assertTrue(exception.getMessage().contains("duplicate hashes"));
	}

	@Test
	public void should_fail_when_no_genesis() {
		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 1L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			Forks.create(Set.of(fork1));
		});

		assertTrue(exception.getMessage().contains("must start at epoch"));
	}

	@Test
	public void should_fail_when_duplicate_epoch() {
		final var fork1 = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), null, 0L);
		final var fork2 = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), null, 2L);
		final var fork3 = new FixedEpochForkConfig("fork3", HashCode.fromInt(3), null, 2L);

		final var exception = assertThrows(IllegalArgumentException.class, () -> {
			Forks.create(Set.of(fork1, fork2, fork3));
		});

		System.out.println(exception.getMessage());
		assertTrue(exception.getMessage().contains("duplicate epoch"));
	}

	private LedgerAndBFTProof proofAtEpoch(ForkConfig currentFork, long epoch) {
		final var ledgerAndBftProof = mock(LedgerAndBFTProof.class);
		final var proof = mock(LedgerProof.class);
		when(proof.getEpoch()).thenReturn(epoch);
		when(ledgerAndBftProof.getProof()).thenReturn(proof);
		return ledgerAndBftProof;
	}
}
