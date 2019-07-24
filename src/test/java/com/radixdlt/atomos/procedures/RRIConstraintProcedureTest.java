package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.stream.Stream;
import org.junit.Test;

public class RRIConstraintProcedureTest {
	private static class CustomParticle extends Particle {
		private RRI rri;

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
}