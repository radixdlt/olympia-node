package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.common.EUID;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.modules.Modules;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atoms.SpunParticle;

public class RRIConstraintProcedureTest {
	private static class CustomParticle extends Particle {
		RRI rri;

		RRI getRRI() {
			return rri;
		}

		@Override
		public String toString() {
			return rri.toString();
		}
	}

	@Test
	public void when_an_rri_is_consumed_with_a_corresponding_particle__then_an_issue_should_not_be_raised() {
		RRIConstraintProcedure procedure = new RRIConstraintProcedure.Builder()
			.add(CustomParticle.class, CustomParticle::getRRI)
			.build();

		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(eq(address))).thenReturn(true);

		CustomParticle customParticle = mock(CustomParticle.class);
		when(customParticle.getRRI()).thenReturn(rri);

		Stream<ProcedureError> issues = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(new RRIParticle(rri)),
				SpunParticle.up(customParticle)
			),
			metadata
		);

		assertThat(issues).isEmpty();
	}

	@Test
	public void when_an_rri_is_consumed_without_a_corresponding_particle__then_an_issue_should_be_raised() {
		RRIConstraintProcedure procedure = new RRIConstraintProcedure.Builder()
			.build();

		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(eq(address))).thenReturn(true);

		Stream<ProcedureError> issues = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(new RRIParticle(rri)),
				SpunParticle.up(mock(CustomParticle.class))
			),
			metadata
		);

		assertThat(issues).anyMatch(i -> i.getErrMsg().contains("unconsumed inputs"));
	}


	@Test
	public void when_an_rri_is_created__then_an_issue_should_be_raised() {
		RRIConstraintProcedure procedure = new RRIConstraintProcedure.Builder()
			.build();

		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		RRI rri = mock(RRI.class);
		when(rri.getAddress()).thenReturn(address);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(eq(address))).thenReturn(true);

		Stream<ProcedureError> issues = procedure.validate(
			ParticleGroup.of(
				SpunParticle.up(new RRIParticle(rri))
			),
			metadata
		);

		assertThat(issues).anyMatch(i -> i.getErrMsg().contains("created"));
	}

	@BeforeClass
	public static void setupSerializer() {
		Serialization s = Serialization.getDefault();
		Modules.put(Serialization.class, s);
	}

	@AfterClass
	public static void cleanup() {
		Modules.remove(Serialization.class);
	}
}