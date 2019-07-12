package com.radixdlt.store;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.Optional;
import org.junit.Test;

public class SpinStateMachineTransitionTest {

	private static StateStore mockedStateProvider(
		Particle particle0, Spin spin0
	) {
		StateStore stateStore = mock(StateStore.class);
		when(stateStore.getSpin(eq(particle0))).thenReturn(Optional.of(spin0));
		return stateStore;
	}

	@Test
	public void when_validating_an_up_or_down_particle_currently_unknown__unknown_state_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mock(StateStore.class);
		when(stateStore.getSpin(any())).thenReturn(Optional.empty());
		when(stateStore.supports(any())).thenReturn(true);

		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP,
			stateStore))
			.isEqualTo(TransitionCheckResult.MISSING_STATE);

		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN,
			stateStore))
			.isEqualTo(TransitionCheckResult.MISSING_STATE);
	}

	@Test
	public void when_validating_a_neutral_particle_currently_anything__illegal_transition_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mock(StateStore.class);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.NEUTRAL, stateStore))
			.isEqualTo(TransitionCheckResult.ILLEGAL_TRANSITION_TO);
	}

	@Test
	public void when_validating_an_up_particle_currently_neutral__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, stateStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_an_up_particle_currently_up__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, stateStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_an_up_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, stateStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_a_down_particle_currently_neutral__missing_dependency_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, stateStore))
			.isEqualTo(TransitionCheckResult.MISSING_DEPENDENCY);
	}

	@Test
	public void when_validating_a_down_particle_currently_up__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, stateStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_a_down_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		StateStore stateStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, stateStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

}