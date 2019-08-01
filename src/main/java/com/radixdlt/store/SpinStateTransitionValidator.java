package com.radixdlt.store;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import java.util.Optional;

/**
 * Validate transitions of an instance of a Particle spin state machine
 * given an existing store.
 */
public class SpinStateTransitionValidator {

	private SpinStateTransitionValidator() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Spin transition issue types
	 */
	public enum TransitionCheckResult {
		CONFLICT,
		MISSING_DEPENDENCY,
		ILLEGAL_TRANSITION_TO,
		MISSING_STATE,
		MISSING_STATE_FROM_UNSUPPORTED_SHARD,
		OKAY
	}

	/**
	 * Checks the transition of a given particle with the given ledger state
	 *
	 * @param particle The particle
	 * @param nextSpin The next spin to check
	 * @param state The state
	 * @return the result of whether the spun particle would be accepted
	 * into current ledger state
	 */
	public static TransitionCheckResult checkParticleTransition(
		Particle particle,
		Spin nextSpin,
		CMStore state
	) {
		if (!SpinStateMachine.canTransitionTo(nextSpin)) {
			return TransitionCheckResult.ILLEGAL_TRANSITION_TO;
		}

		final Optional<Spin> currentSpinOptional = state.getSpin(particle);
		if (currentSpinOptional.isPresent()) {
			final Spin currentSpin = currentSpinOptional.get();
			if (!SpinStateMachine.canTransition(currentSpin, nextSpin)) {
				if (!SpinStateMachine.isBefore(currentSpin, nextSpin)) {
					return TransitionCheckResult.CONFLICT;
				} else {
					return TransitionCheckResult.MISSING_DEPENDENCY;
				}
			}
		} else {
			if (!state.supports(particle.getDestinations())) {
				return TransitionCheckResult.MISSING_STATE_FROM_UNSUPPORTED_SHARD;
			} else {
				return TransitionCheckResult.MISSING_STATE;
			}
		}

		return TransitionCheckResult.OKAY;
	}
}
