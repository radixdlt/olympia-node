/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.universe.Universe;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AtomCheckHookTest {
	@Test
	public void when_validating_atom_with_particles__result_has_no_error() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);
		ECSignature ecSignature = new ECSignature(BigInteger.ONE, BigInteger.ONE);

		Atom atom = new Atom(
			ImmutableList.of(mock(ParticleGroup.class)),
			ImmutableMap.of(mock(EUID.class),
				ecSignature), ImmutableMap.of("timestamp", "0")
		);

		assertThat(atomCheckHook.hook(atom).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);

		ECSignature ecSignature = new ECSignature(BigInteger.ONE, BigInteger.ONE);

		Atom atom = new Atom(
			ImmutableList.of(),
			ImmutableMap.of(mock(EUID.class),
				ecSignature), ImmutableMap.of("timestamp", "0")
		);

		assertThat(atomCheckHook.hook(atom).getErrorMessage())
			.contains("instructions");
	}

	@Test
	public void when_validating_atom_without_metadata__result_has_error() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);

		ECSignature ecSignature = new ECSignature(BigInteger.ONE, BigInteger.ONE);

		Atom atom = new Atom(
			ImmutableList.of(mock(ParticleGroup.class)),
			ImmutableMap.of(mock(EUID.class),
				ecSignature), ImmutableMap.of()
		);

		assertThat(atomCheckHook.hook(atom).getErrorMessage())
			.contains("metadata does not contain");
	}

	@Test
	public void when_validating_atom_with_bad_timestamp__result_has_error() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);

		ECSignature ecSignature = new ECSignature(BigInteger.ONE, BigInteger.ONE);

		Atom atom = new Atom(
			ImmutableList.of(mock(ParticleGroup.class)),
			ImmutableMap.of(mock(EUID.class),
				ecSignature), ImmutableMap.of("timestamp", "badinput")
		);

		assertThat(atomCheckHook.hook(atom).getErrorMessage())
			.contains("invalid timestamp");
	}
}