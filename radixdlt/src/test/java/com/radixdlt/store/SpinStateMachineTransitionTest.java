package com.radixdlt.store;

import java.util.Optional;
import org.junit.Test;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class SpinStateMachineTransitionTest {

	private static CMStore mockedStateProvider(
		Particle particle0, Spin spin0
	) {
		CMStore cmStore = mock(CMStore.class);
		when(cmStore.getSpin(eq(particle0))).thenReturn(Optional.of(spin0));
		return cmStore;
	}

	@Test
	public void when_validating_an_up_or_down_particle_currently_unknown__unknown_state_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mock(CMStore.class);
		when(cmStore.getSpin(any())).thenReturn(Optional.empty());
		when(cmStore.supports(any())).thenReturn(true);

		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, cmStore))
			.isEqualTo(TransitionCheckResult.MISSING_STATE);

		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, cmStore))
			.isEqualTo(TransitionCheckResult.MISSING_STATE);
	}

	@Test
	public void when_validating_a_neutral_particle_currently_anything__illegal_transition_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mock(CMStore.class);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.NEUTRAL, cmStore))
			.isEqualTo(TransitionCheckResult.ILLEGAL_TRANSITION_TO);
	}

	@Test
	public void when_validating_an_up_particle_currently_neutral__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, cmStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_an_up_particle_currently_up__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, cmStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_an_up_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, cmStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_a_down_particle_currently_neutral__missing_dependency_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, cmStore))
			.isEqualTo(TransitionCheckResult.MISSING_DEPENDENCY);
	}

	@Test
	public void when_validating_a_down_particle_currently_up__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, cmStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_a_down_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		CMStore cmStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, cmStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

}