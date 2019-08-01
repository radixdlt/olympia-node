package com.radixdlt.engine;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.IndexedParticleGroup;
import com.radixdlt.atoms.IndexedSpunParticle;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMErrorCode;
import java.util.stream.Stream;
import org.junit.Test;

public class RadixEngineUtilsTest {
	@Test
	public void when_validating_an_up_cm_particle__no_issue_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThat(
			RadixEngineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0))
				)
			)
		).isEmpty();
	}

	@Test
	public void when_validating_an_up_to_down_cm_particle__no_issue_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThat(
			RadixEngineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(1, 0))
				)
			)
		).isEmpty();
	}

	@Test
	public void when_validating_an_up_to_up_cm_particle__internal_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThat(
			RadixEngineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(1, 0))
				)
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_validating_a_down_to_down_cm_particle__conflict_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThat(
			RadixEngineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(1, 0))
				)
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_validating_a_down_to_up_cm_particle__single_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);

		assertThat(
			RadixEngineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(1, 0))
				)
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_checking_an_empty_particle_group__error_is_returned() {
		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.indexedParticleGroups()).thenReturn(Stream.of(
			new IndexedParticleGroup(ParticleGroup.of(), 0)
		));
		assertThat(RadixEngineUtils.checkParticleGroupsNotEmpty(atom))
			.containsExactly(new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.EMPTY_PARTICLE_GROUP));
	}

	@Test
	public void when_checking_two_empty_particle_groups__two_errors_are_returned() {
		ImmutableAtom atom = mock(ImmutableAtom.class);
		when(atom.indexedParticleGroups()).thenReturn(Stream.of(
			new IndexedParticleGroup(ParticleGroup.of(), 0),
			new IndexedParticleGroup(ParticleGroup.of(), 1)
		));
		assertThat(RadixEngineUtils.checkParticleGroupsNotEmpty(atom))
			.containsExactly(
				new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.EMPTY_PARTICLE_GROUP),
				new CMError(DataPointer.ofParticleGroup(1), CMErrorCode.EMPTY_PARTICLE_GROUP)
			);
	}

	@Test
	public void when_checking_two_duplicate_particles__two_errors_are_returned() {
		ImmutableAtom atom = mock(ImmutableAtom.class);
		Particle particle = mock(Particle.class);
		when(atom.indexedParticleGroups()).thenReturn(Stream.of(
			new IndexedParticleGroup(ParticleGroup.of(
				SpunParticle.up(particle),
				SpunParticle.down(particle)
			), 0)
		));

		assertThat(RadixEngineUtils.checkParticleTransitionsUniqueInGroup(atom))
			.containsExactly(new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.DUPLICATE_PARTICLES_IN_GROUP));
	}
}