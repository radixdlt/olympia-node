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
		private final Object remainder;

		private ProcedureResult(CMAction cmAction, Object remainder) {
			this.cmAction = cmAction;
			this.remainder = remainder;
		}

		public static ProcedureResult popInput(Object outputRemainder) {
			return new ProcedureResult(CMAction.POP_INPUT, outputRemainder);
		}

		public static ProcedureResult popOutput(Object inputRemainder) {
			return new ProcedureResult(CMAction.POP_OUTPUT, inputRemainder);
		}

		public static ProcedureResult popInputOutput() {
			return new ProcedureResult(CMAction.POP_INPUT_OUTPUT, null);
		}

		public static ProcedureResult error() {
			return new ProcedureResult(CMAction.ERROR, null);
		}

		public CMAction getCmAction() {
			return cmAction;
		}

		public <T> Optional<T> getInputRemainder(Class<T> remainderClass) {
			return this.cmAction == CMAction.POP_OUTPUT ? Optional.ofNullable(remainderClass.cast(this.remainder)) : Optional.empty();
		}

		public <T> Optional<T> getOutputRemainder(Class<T> remainderClass) {
			return this.cmAction == CMAction.POP_INPUT ? Optional.ofNullable(remainderClass.cast(this.remainder)) : Optional.empty();
		}

		public Object getRemainder() {
			return remainder;
		}
	}

	ProcedureResult execute(
		T inputParticle,
		U outputParticle,
		ProcedureResult prevResult
	);
}
