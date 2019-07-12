package com.radixdlt.atomos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.test.TestAtomOSKernel;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class AtomOSKernelConstraintScryptTest {
	private static TestAtomOSKernel testAtomDriver;
	private static Serialization serialization;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		Universe universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(Collections.emptyList());
		serialization = mock(Serialization.class);

		testAtomDriver = new TestAtomOSKernel(universe);
		AtomDriver scrypt = new AtomDriver(serialization, false, 30);
		scrypt.main(testAtomDriver);
	}

	@Test
	public void when_validating_atom_with_size_below_limit__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[AtomDriver.MAX_ATOM_SIZE]);
		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertNoErrorWithMessageContaining("size");
	}

	@Test
	public void when_validating_atom_with_size_above_limit__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[AtomDriver.MAX_ATOM_SIZE + 1]);
		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("size");
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);
		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.spunParticles()).thenReturn(Stream.of(mock(SpunParticle.class)));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertNoErrorWithMessageContaining("particles");
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.spunParticles()).thenReturn(Stream.of());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("particles");
	}

	@Test
	public void when_validating_atom_with_signatures__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getSignatures()).thenReturn(Collections.singletonMap(mock(EUID.class), mock(ECSignature.class)));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertNoErrorWithMessageContaining("signatures");
	}

	@Test
	public void when_validating_atom_without_signatures__result_as_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getSignatures()).thenReturn(Collections.emptyMap());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("signatures");
	}

	@Test
	public void when_validating_atom_without_fee__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("metadata does not contain");
	}

	@Test
	public void when_validating_atom_without_timestamp__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.emptyMap());
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("metadata does not contain");
	}

	@Test
	public void when_validating_atom_with_bad_timestamp__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.singletonMap(ImmutableAtom.METADATA_TIMESTAMP_KEY, "badinput"));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("invalid timestamp");
	}

	@Test
	public void when_validating_atom_with_timestamp_after_current__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.singletonMap(ImmutableAtom.METADATA_TIMESTAMP_KEY, "1000000"));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		testAtomDriver.setCurrentTimestamp(9L);
		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("after allowed drift");
	}

	@Test
	public void when_validating_atom_with_timestamp_before_creation__result_has_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.singletonMap(ImmutableAtom.METADATA_TIMESTAMP_KEY, "10"));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		Universe universe = mock(Universe.class);
		when(universe.getTimestamp()).thenReturn(11L);

		testAtomDriver.setUniverse(universe);
		testAtomDriver.setCurrentTimestamp(10L);
		testAtomDriver.testAtom(cmAtom)
			.assertErrorWithMessageContaining("before universe");
	}

	@Test
	public void when_validating_atom_with_valid_timestamp__result_has_no_error() throws Exception {
		when(serialization.toDson(any(), any())).thenReturn(new byte[1]);

		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.getMetaData()).thenReturn(Collections.singletonMap(ImmutableAtom.METADATA_TIMESTAMP_KEY, "10"));
		CMAtom cmAtom = mock(CMAtom.class);
		when(cmAtom.getAtom()).thenReturn(atom);

		Universe universe = mock(Universe.class);
		when(universe.getTimestamp()).thenReturn(10L);

		testAtomDriver.setUniverse(universe);
		testAtomDriver.setCurrentTimestamp(10L);
		testAtomDriver.testAtom(cmAtom)
			.assertNoErrorWithMessageContaining("timestamp");
	}
}