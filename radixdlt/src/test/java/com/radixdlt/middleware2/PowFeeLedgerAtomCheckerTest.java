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

package com.radixdlt.middleware2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.universe.Universe;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PowFeeLedgerAtomCheckerTest {
	private PowFeeLedgerAtomChecker checker;
	private Universe universe;
	private PowFeeComputer powFeeComputer;
	private Hash target;

	@Before
	public void setUp() {
		this.universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		this.powFeeComputer = mock(PowFeeComputer.class);
		this.target = mock(Hash.class);
		this.checker = new PowFeeLedgerAtomChecker(
			universe,
			powFeeComputer,
			target
		);
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() {
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		ClientAtom ledgerAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(mock(CMMicroInstruction.class)), Hash.random(), ImmutableMap.of()
		);
		when(ledgerAtom.getAID()).thenReturn(mock(AID.class));
		when(ledgerAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(ledgerAtom.getMetaData()).thenReturn(
			ImmutableMap.of(
				"timestamp", "0",
				"powNonce", "0"
			)
		);
		Hash powSpent = mock(Hash.class);
		when(powFeeComputer.computePowSpent(eq(ledgerAtom), eq(0L))).thenReturn(powSpent);
		when(powSpent.compareTo(eq(target))).thenReturn(-1);
		assertThat(checker.check(ledgerAtom, ImmutableSet.of()).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() {
		ClientAtom ledgerAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(), Hash.random(), ImmutableMap.of()
		);
		when(ledgerAtom.getAID()).thenReturn(mock(AID.class));
		when(ledgerAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(ledgerAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(checker.check(ledgerAtom, ImmutableSet.of()).getErrorMessage())
			.contains("instructions");
	}

	@Test
	public void when_validating_atom_without_metadata__result_has_error() {
		LedgerAtom ledgerAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(mock(CMMicroInstruction.class)), Hash.random(), ImmutableMap.of()
		);
		when(ledgerAtom.getAID()).thenReturn(mock(AID.class));
		when(ledgerAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(ledgerAtom.getMetaData()).thenReturn(ImmutableMap.of());

		assertThat(checker.check(ledgerAtom, ImmutableSet.of()).getErrorMessage())
			.contains("metadata does not contain");
	}
}