package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class AtomOSKernelConstraintScryptTest {
	private static CMAtomOS cmAtomOS;
	private static Serialization serialization;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		serialization = mock(Serialization.class);

		cmAtomOS = new CMAtomOS();
		AtomDriver scrypt = new AtomDriver(
			() -> universe,
			() -> 0,
			serialization,
			true,
			30
		);
		cmAtomOS.loadKernelConstraintScrypt(scrypt);
	}

	@Test
	public void when_validating_atom_with_size_below_limit__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[AtomDriver.MAX_ATOM_SIZE]);
		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(cmAtomOS.testAtom(cmAtom)).isNotPresent();
	}

	@Test
	public void when_validating_atom_with_size_above_limit__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[AtomDriver.MAX_ATOM_SIZE + 1]);
		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("size"));
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);
		ImmutableAtom atom = mock(ImmutableAtom.class);

		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "0"));

		assertThat(cmAtomOS.testAtom(cmAtom)).isNotPresent();
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.spunParticles()).thenReturn(Stream.of());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
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
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getSignatures()).thenReturn(Collections.emptyMap());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
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
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
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
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.emptyMap());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
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
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);
		when(cmAtom.getParticles()).thenReturn(ImmutableList.of(mock(CMParticle.class)));
		when(cmAtom.getSignatures()).thenReturn(ImmutableMap.of(mock(EUID.class), mock(ECSignature.class)));
		when(cmAtom.getMetaData()).thenReturn(ImmutableMap.of("timestamp", "badinput"));

		assertThat(cmAtomOS.testAtom(cmAtom))
			.isPresent()
			.get()
			.matches(p -> p.getErrMsg().contains("invalid timestamp"));
	}
}