package com.radixdlt.constraintmachine;

/**
 * Validates whether a specific transition procedure is permissible
 * @param <P> particle class
 */
public interface WitnessValidator<P extends Particle> {
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
		P particle,
		WitnessData witnessData
	);
}
