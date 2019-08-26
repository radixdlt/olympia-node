package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.universe.Universe;
import java.util.Collections;
import org.junit.Test;

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

		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(Pair.of(mock(CMMicroInstruction.class), mock(DataPointer.class))));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));
		SimpleRadixEngineAtom cmAtom = new SimpleRadixEngineAtom(immutableAtom, cmInstruction);

		assertThat(atomCheckHook.hook(cmAtom).isSuccess()).isTrue();
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

		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of());
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));
		SimpleRadixEngineAtom cmAtom = new SimpleRadixEngineAtom(immutableAtom, cmInstruction);

		assertThat(atomCheckHook.hook(cmAtom).getErrorMessage())
			.contains("particles");
	}

	@Test
	public void when_validating_atom_without_metadata__result_has_error() throws Exception {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);

		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(Pair.of(mock(CMMicroInstruction.class), mock(DataPointer.class))));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of());
		SimpleRadixEngineAtom cmAtom = new SimpleRadixEngineAtom(immutableAtom, cmInstruction);

		assertThat(atomCheckHook.hook(cmAtom).getErrorMessage())
			.contains("metadata does not contain");
	}

	@Test
	public void when_validating_atom_with_bad_timestamp__result_has_error() throws Exception {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		AtomCheckHook atomCheckHook = new AtomCheckHook(
			() -> universe,
			() -> 0,
			true,
			30
		);

		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(Pair.of(mock(CMMicroInstruction.class), mock(DataPointer.class))));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "badinput"));
		SimpleRadixEngineAtom cmAtom = new SimpleRadixEngineAtom(immutableAtom, cmInstruction);

		assertThat(atomCheckHook.hook(cmAtom).getErrorMessage())
			.contains("invalid timestamp");
	}
}