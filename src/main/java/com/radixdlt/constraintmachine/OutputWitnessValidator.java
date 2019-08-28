package com.radixdlt.constraintmachine;

public interface OutputWitnessValidator<T extends Particle> {
	final class OutputWitnessValidatorResult {
		private final String errorMessage;

		OutputWitnessValidatorResult(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public static OutputWitnessValidatorResult success() {
			return new OutputWitnessValidatorResult(null);
		}

		public static OutputWitnessValidatorResult error(String errorMessage) {
			return new OutputWitnessValidatorResult(errorMessage);
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

	OutputWitnessValidatorResult validate(T outputParticle, WitnessData witnessData);
}
