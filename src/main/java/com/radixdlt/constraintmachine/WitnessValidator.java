package com.radixdlt.constraintmachine;

import com.radixdlt.constraintmachine.TransitionProcedure.CMAction;

/**
 * Validates whether a specific transition procedure is permissible
 * @param <T> input particle class
 * @param <U> output particle class
 */
public interface WitnessValidator<T extends Particle, U extends Particle> {
	final class WitnessValidatorResult {
		private final String errorMessage;

		WitnessValidatorResult(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public static WitnessValidatorResult success() {
			return new WitnessValidatorResult(null);
		}

		public static WitnessValidatorResult error(String errorMessage) {
			return new WitnessValidatorResult(errorMessage);
		}

		public boolean isSuccess() {
			return this.errorMessage == null;
		}

		public boolean isError() {
			return this.errorMessage != null;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}

	WitnessValidatorResult validate(
		CMAction result,
		T inputParticle,
		U outputParticle,
		WitnessData witnessData
	);
}
