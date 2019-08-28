package com.radixdlt.constraintmachine;

import java.util.Objects;

public interface OutputProcedure<T extends Particle> {
	final class OutputProcedureResult {
		private final String errorMessage;

		private OutputProcedureResult(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public static OutputProcedureResult pop() {
			return new OutputProcedureResult(null);
		}

		public static OutputProcedureResult error(String errorMessage) {
			Objects.requireNonNull(errorMessage);
			return new OutputProcedureResult(errorMessage);
		}

		public boolean isSuccess() {
			return this.errorMessage == null;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}

	OutputProcedureResult execute(T outputParticle);
}
