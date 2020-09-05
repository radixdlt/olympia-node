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

import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class VerifiedLedgerStateAndProofTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(VerifiedLedgerStateAndProof.class)
			.verify();
	}

	@Test
	public void testComparsionBetweenDifferentEpochs() {
		LedgerState l0 = mock(LedgerState.class);
		when(l0.getEpoch()).thenReturn(1L);
		VerifiedLedgerStateAndProof s0 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerState l1 = mock(LedgerState.class);
		when(l1.getEpoch()).thenReturn(2L);
		VerifiedLedgerStateAndProof s1 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(s0).isLessThan(s1);
	}

	@Test
	public void testComparsionBetweenDifferentStateVersions() {
		LedgerState l0 = mock(LedgerState.class);
		when(l0.getEpoch()).thenReturn(2L);
		when(l0.getStateVersion()).thenReturn(2L);
		VerifiedLedgerStateAndProof s0 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerState l1 = mock(LedgerState.class);
		when(l1.getEpoch()).thenReturn(2L);
		when(l1.getEpoch()).thenReturn(3L);
		VerifiedLedgerStateAndProof s1 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(s0).isLessThan(s1);
	}

	@Test
	public void testComparsionWithEndOfEpoch() {
		LedgerState l0 = mock(LedgerState.class);
		when(l0.getEpoch()).thenReturn(2L);
		when(l0.getStateVersion()).thenReturn(3L);
		when(l0.isEndOfEpoch()).thenReturn(false);
		VerifiedLedgerStateAndProof s0 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerState l1 = mock(LedgerState.class);
		when(l1.getEpoch()).thenReturn(2L);
		when(l1.getEpoch()).thenReturn(3L);
		when(l1.isEndOfEpoch()).thenReturn(true);
		VerifiedLedgerStateAndProof s1 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(s0).isLessThan(s1);
	}

	@Test
	public void testComparsionEqual() {
		LedgerState l0 = mock(LedgerState.class);
		when(l0.getEpoch()).thenReturn(2L);
		when(l0.getStateVersion()).thenReturn(3L);
		when(l0.isEndOfEpoch()).thenReturn(true);
		VerifiedLedgerStateAndProof s0 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l0,
			mock(TimestampedECDSASignatures.class)
		);
		LedgerState l1 = mock(LedgerState.class);
		when(l1.getEpoch()).thenReturn(2L);
		when(l1.getEpoch()).thenReturn(3L);
		when(l1.isEndOfEpoch()).thenReturn(true);
		VerifiedLedgerStateAndProof s1 = new VerifiedLedgerStateAndProof(
			mock(BFTHeader.class), mock(BFTHeader.class), 0, mock(Hash.class),
			l1,
			mock(TimestampedECDSASignatures.class)
		);
		assertThat(s0).isLessThan(s1);
	}
}