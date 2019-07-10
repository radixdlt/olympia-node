package com.radixdlt.constraintmachine;

import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import org.radix.atoms.Atom;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.IndexedSpunParticle;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.store.StateStore;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class ConstraintMachineUtilsTest {

	private static StateStore mockedStateProvider(
		Particle particle0, Spin spin0
	) {
		StateStore stateStore = mock(StateStore.class);
		when(stateStore.getSpin(eq(particle0))).thenReturn(Optional.of(spin0));
		return stateStore;
	}

	private static StateStore mockedStateProvider(
		Particle particle0, Spin spin0,
		Particle particle1, Spin spin1
	) {
		StateStore stateStore = mock(StateStore.class);
		doReturn(Optional.of(spin0)).when(stateStore).getSpin(eq(particle0));
		doReturn(Optional.of(spin1)).when(stateStore).getSpin(eq(particle1));
		return stateStore;
	}

	@Test
	public void when_validating_an_up_cm_particle_with_neutral_store__no_issue_is_returned() {
		Particle particle0 = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(
			particle0, Spin.NEUTRAL
		);

		assertThat(
			ConstraintMachineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0))
				),
				stateStore
			)
		).isEmpty();
	}

	@Test
	public void when_validating_an_up_to_down_cm_particle_with_neutral_store__no_issue_is_returned() {
		Particle particle0 = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(
			particle0, Spin.NEUTRAL
		);

		assertThat(
			ConstraintMachineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(1, 0))
				),
				stateStore
			)
		).isEmpty();
	}

	@Test
	public void when_validating_an_up_to_up_cm_particle_with_neutral_store__internal_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(
			particle0, Spin.NEUTRAL
		);

		assertThat(
			ConstraintMachineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(1, 0))
				),
				stateStore
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_validating_a_down_to_down_cm_particle_with_up_store__conflict_is_returned() {
		Particle particle0 = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(
			particle0, Spin.UP
		);

		assertThat(
			ConstraintMachineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(1, 0))
				),
				stateStore
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_validating_a_down_to_up_cm_particle_with_up_store__single_conflict_is_returned() {
		Particle particle0 = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(
			particle0, Spin.UP
		);

		assertThat(
			ConstraintMachineUtils.checkInternalSpins(
				ImmutableList.of(
					new IndexedSpunParticle(SpunParticle.down(particle0), DataPointer.ofParticle(0, 0)),
					new IndexedSpunParticle(SpunParticle.up(particle0), DataPointer.ofParticle(1, 0))
				),
				stateStore
			)
		).containsExactly(
			new CMError(DataPointer.ofParticle(1, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT)
		);
	}

	@Test
	public void when_checking_an_empty_particle_group__error_is_returned() {
		Atom atom = new Atom();
		atom.addParticleGroup(ParticleGroup.of());
		assertThat(ConstraintMachineUtils.checkParticleGroupsNotEmpty(atom))
			.containsExactly(new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.EMPTY_PARTICLE_GROUP));
	}

	@Test
	public void when_checking_two_empty_particle_groups__two_errors_are_returned() {
		Atom atom = new Atom();
		atom.addParticleGroup(ParticleGroup.of());
		atom.addParticleGroup(ParticleGroup.of());
		assertThat(ConstraintMachineUtils.checkParticleGroupsNotEmpty(atom))
			.containsExactly(
				new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.EMPTY_PARTICLE_GROUP),
				new CMError(DataPointer.ofParticleGroup(1), CMErrorCode.EMPTY_PARTICLE_GROUP)
			);
	}

	@Test
	public void when_checking_two_duplicate_particles__two_errors_are_returned() {
		Atom atom = new Atom();
		Particle particle = mock(Particle.class);
		atom.addParticleGroup(
			ParticleGroup.of(
				SpunParticle.up(particle),
				SpunParticle.down(particle)
			)
		);
		assertThat(ConstraintMachineUtils.checkParticleTransitionsUniqueInGroup(atom))
			.containsExactly(new CMError(DataPointer.ofParticleGroup(0), CMErrorCode.DUPLICATE_PARTICLES_IN_GROUP));
	}
}