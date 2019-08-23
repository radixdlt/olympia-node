package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.engine.SimpleCMAtom;
import com.radixdlt.universe.Universe;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.Test;

public class AtomOSKernelConstraintScryptTest {
	private static CMAtomOS cmAtomOS;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());

		cmAtomOS = new CMAtomOS();
		AtomDriver scrypt = new AtomDriver(
			() -> universe,
			() -> 0,
			true,
			30
		);
		cmAtomOS.loadKernelConstraintScrypt(scrypt);
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws Exception {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));
		SimpleCMAtom cmAtom = new SimpleCMAtom(immutableAtom, cmInstruction);

		assertThat(cmAtomOS.testAtom(cmAtom)).isNotPresent();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() throws Exception {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of());
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));
		SimpleCMAtom cmAtom = new SimpleCMAtom(immutableAtom, cmInstruction);

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("particles"));
	}

	@Test
	public void when_validating_atom_without_signatures__result_has_error() throws Exception {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of());
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of());
		SimpleCMAtom cmAtom = new SimpleCMAtom(immutableAtom, cmInstruction);

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("signatures"));
	}

	@Test
	public void when_validating_atom_without_metadata__result_has_error() throws Exception {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of());
		SimpleCMAtom cmAtom = new SimpleCMAtom(immutableAtom, cmInstruction);

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("metadata does not contain"));
	}

	@Test
	public void when_validating_atom_with_bad_timestamp__result_has_error() throws Exception {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmInstruction.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		ImmutableAtom immutableAtom = mock(ImmutableAtom.class);
		when(immutableAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "badinput"));
		SimpleCMAtom cmAtom = new SimpleCMAtom(immutableAtom, cmInstruction);

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("invalid timestamp"));
	}
}