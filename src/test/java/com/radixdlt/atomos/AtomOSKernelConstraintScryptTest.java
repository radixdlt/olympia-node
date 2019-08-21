package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.crypto.ECSignature;
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
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(cmAtomOS.testAtom(cmAtom)).isNotPresent();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() throws Exception {
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of());
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("particles"));
	}

	@Test
	public void when_validating_atom_without_signatures__result_has_error() throws Exception {
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of());
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of());

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("signatures"));
	}

	@Test
	public void when_validating_atom_without_fee__result_has_error() throws Exception {
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of());

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("metadata does not contain"));
	}

	@Test
	public void when_validating_atom_without_timestamp__result_has_error() throws Exception {
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of());
		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("metadata does not contain"));
	}

	@Test
	public void when_validating_atom_with_bad_timestamp__result_has_error() throws Exception {
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "badinput"));

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("invalid timestamp"));
	}
}