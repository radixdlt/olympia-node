package com.radixdlt.constraintmachine;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> {

	final class ProcedureResult {
		private final CMAction cmAction;
		private final UsedData used;
		private final String errorMessage;

		private ProcedureResult(CMAction cmAction, UsedData used, String errorMessage) {
			this.cmAction = cmAction;
			this.used = used;
			this.errorMessage = errorMessage;
		}

		public static ProcedureResult popInput(UsedData outputUsed) {
			return new ProcedureResult(CMAction.POP_INPUT, outputUsed, null);
		}

		public static ProcedureResult popOutput(UsedData inputUsed) {
			return new ProcedureResult(CMAction.POP_OUTPUT, inputUsed, null);
		}

		public static ProcedureResult popInputOutput() {
			return new ProcedureResult(CMAction.POP_INPUT_OUTPUT, null, null);
		}

		public static ProcedureResult error(String errorMessage) {
			return new ProcedureResult(CMAction.ERROR, null, errorMessage);
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public CMAction getCmAction() {
			return cmAction;
		}

		public UsedData getUsed() {
			return used;
		}
	}

	ProcedureResult execute(
		I inputParticle, N inputUsed,
		O outputParticle, U outputUsed
	);
}
