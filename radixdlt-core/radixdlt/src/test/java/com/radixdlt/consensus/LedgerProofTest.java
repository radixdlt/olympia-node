/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof.OrderByEpochAndVersionComparator;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class LedgerProofTest {
	private OrderByEpochAndVersionComparator headerComparator;

	@Before
	public void setup() {
		this.headerComparator = new OrderByEpochAndVersionComparator();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LedgerProof.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void testGetters() {
		LedgerHeader l0 = mock(LedgerHeader.class);
		HashCode accumulatorHash = mock(HashCode.class);
		View view = mock(View.class);
		when(l0.getEpoch()).thenReturn(3L);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getAccumulatorHash()).thenReturn(accumulatorHash);
		when(accumulatorState.getStateVersion()).thenReturn(12345L);
		when(l0.getAccumulatorState()).thenReturn(accumulatorState);
		when(l0.getView()).thenReturn(view);
		when(l0.timestamp()).thenReturn(2468L);
		when(l0.isEndOfEpoch()).thenReturn(true);
		var ledgerHeaderAndProof = new LedgerProof(
			HashUtils.random256(),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(ledgerHeaderAndProof.getEpoch()).isEqualTo(3L);
		assertThat(ledgerHeaderAndProof.getStateVersion()).isEqualTo(12345L);
		assertThat(ledgerHeaderAndProof.getView()).isEqualTo(view);
		assertThat(ledgerHeaderAndProof.timestamp()).isEqualTo(2468L);
		assertThat(ledgerHeaderAndProof.isEndOfEpoch()).isTrue();
	}

	@Test
	public void testComparsionBetweenDifferentEpochs() {
		LedgerHeader l0 = mock(LedgerHeader.class);
		when(l0.getEpoch()).thenReturn(1L);
		var s0 = new LedgerProof(
			HashUtils.random256(),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerHeader l1 = mock(LedgerHeader.class);
		when(l1.getEpoch()).thenReturn(2L);
		LedgerProof s1 = new LedgerProof(
			HashUtils.random256(),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(headerComparator.compare(s0, s1)).isNegative();
		assertThat(headerComparator.compare(s1, s0)).isPositive();
	}

	@Test
	public void testComparsionBetweenDifferentStateVersions() {
		LedgerHeader l0 = mock(LedgerHeader.class);
		when(l0.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(2L);
		when(l0.getAccumulatorState()).thenReturn(accumulatorState);
		LedgerProof s0 = new LedgerProof(
			HashUtils.random256(),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerHeader l1 = mock(LedgerHeader.class);
		when(l1.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
		when(accumulatorState1.getStateVersion()).thenReturn(3L);
		when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
		LedgerProof s1 = new LedgerProof(
			HashUtils.random256(),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(headerComparator.compare(s0, s1)).isNegative();
		assertThat(headerComparator.compare(s1, s0)).isPositive();
	}

	@Test
	public void testComparsionWithEndOfEpoch() {
		LedgerHeader l0 = mock(LedgerHeader.class);
		when(l0.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(2L);
		when(l0.getAccumulatorState()).thenReturn(accumulatorState);
		when(l0.isEndOfEpoch()).thenReturn(false);
		LedgerProof s0 = new LedgerProof(
			HashUtils.random256(),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerHeader l1 = mock(LedgerHeader.class);
		when(l1.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
		when(accumulatorState1.getStateVersion()).thenReturn(3L);
		when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
		when(l1.isEndOfEpoch()).thenReturn(true);
		LedgerProof s1 = new LedgerProof(
			HashUtils.random256(),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(headerComparator.compare(s0, s1)).isNegative();
		assertThat(headerComparator.compare(s1, s0)).isPositive();
	}

	@Test
	public void testComparsionEqual() {
		LedgerHeader l0 = mock(LedgerHeader.class);
		when(l0.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(3L);
		when(l0.getAccumulatorState()).thenReturn(accumulatorState);
		when(l0.isEndOfEpoch()).thenReturn(true);
		LedgerProof s0 = new LedgerProof(
			HashUtils.random256(),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerHeader l1 = mock(LedgerHeader.class);
		when(l1.getEpoch()).thenReturn(2L);
		AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
		when(accumulatorState1.getStateVersion()).thenReturn(3L);
		when(l1.getAccumulatorState()).thenReturn(accumulatorState1);
		when(l1.isEndOfEpoch()).thenReturn(true);
		LedgerProof s1 = new LedgerProof(
			HashUtils.random256(),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(headerComparator.compare(s0, s1)).isZero();
		assertThat(headerComparator.compare(s1, s0)).isZero();
	}
}