package com.radixdlt.store;

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

	private static EngineStore mockedStateProvider(
		Particle particle0, Spin spin0
	) {
		EngineStore engineStore = mock(EngineStore.class);
		when(engineStore.supports(any())).thenReturn(true);
		when(engineStore.getSpin(eq(particle0))).thenReturn(spin0);
		return engineStore;
	}

	@Test
	public void when_validating_a_neutral_particle_currently_anything__illegal_transition_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mock(EngineStore.class);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.NEUTRAL, engineStore))
			.isEqualTo(TransitionCheckResult.ILLEGAL_TRANSITION_TO);
	}

	@Test
	public void when_validating_an_up_particle_currently_neutral__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, engineStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_an_up_particle_currently_up__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, engineStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_an_up_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.UP, engineStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

	@Test
	public void when_validating_a_down_particle_currently_neutral__missing_dependency_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.NEUTRAL);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, engineStore))
			.isEqualTo(TransitionCheckResult.MISSING_DEPENDENCY);
	}

	@Test
	public void when_validating_a_down_particle_currently_up__no_issue_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.UP);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, engineStore))
			.isEqualTo(TransitionCheckResult.OKAY);
	}

	@Test
	public void when_validating_a_down_particle_currently_down__conflict_is_returned() {
		Particle particle = mock(Particle.class);
		EngineStore engineStore = mockedStateProvider(particle, Spin.DOWN);
		assertThat(SpinStateTransitionValidator.checkParticleTransition(particle, Spin.DOWN, engineStore))
			.isEqualTo(TransitionCheckResult.CONFLICT);
	}

}