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

package com.radixdlt.statecomputer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.atom.Atom;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class CommittedAtomTest {
	private CommittedAtom committedAtom;
	private Atom atom;
	private VerifiedLedgerHeaderAndProof proof;

	@Before
	public void setUp() {
		this.atom = mock(Atom.class);
		when(atom.getAID()).thenReturn(mock(AID.class));
		when(atom.getCMInstruction()).thenReturn(mock(CMInstruction.class));
		when(atom.getMessage()).thenReturn("test message");
		this.proof = mock(VerifiedLedgerHeaderAndProof.class);
		this.committedAtom = CommittedAtom.create(atom, proof);
	}

	@Test
	public void testGetters() {
		assertThat(committedAtom.getClientAtom()).isEqualTo(atom);
		assertThat(committedAtom.getAID()).isEqualTo(atom.getAID());
		assertThat(committedAtom.getCMInstruction()).isEqualTo(atom.getCMInstruction());
		assertThat(committedAtom.getMessage()).isEqualTo(atom.getMessage());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CommittedAtom.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void sensibleToString() {
		Assertions.assertThat(committedAtom.toString()).contains(CommittedAtom.class.getSimpleName());
	}
}