package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import java.util.Optional;

/**
 * Application level "Bytecode" to be run per particle in the Constraint machine
 */
public interface TransitionProcedure<T extends Particle, U extends Particle> {
	enum CMAction {
		POP_INPUT,
		POP_OUTPUT,
		POP_INPUT_OUTPUT,
		ERROR
	}

	final class ProcedureResult {
		private final CMAction cmAction;
		private final Object used;
		private final String errorMessage;

		private ProcedureResult(CMAction cmAction, Object used, String errorMessage) {
			this.cmAction = cmAction;
			this.used = used;
			this.errorMessage = errorMessage;
		}

		public static ProcedureResult popInput(Object outputUsed) {
			return new ProcedureResult(CMAction.POP_INPUT, outputUsed, null);
		}

		public static ProcedureResult popOutput(Object inputUsed) {
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

		public <T> Optional<T> getInputUsed(Class<T> remainderClass) {
			return this.cmAction == CMAction.POP_OUTPUT ? Optional.ofNullable(remainderClass.cast(this.used)) : Optional.empty();
		}

		public <T> Optional<T> getOutputUsed(Class<T> remainderClass) {
			return this.cmAction == CMAction.POP_INPUT ? Optional.ofNullable(remainderClass.cast(this.used)) : Optional.empty();
		}

		public Object getUsed() {
			return used;
		}
	}

	ProcedureResult execute(
		T inputParticle,
		U outputParticle,
		ProcedureResult prevResult
	);
}
