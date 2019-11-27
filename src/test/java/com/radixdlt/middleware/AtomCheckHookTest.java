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