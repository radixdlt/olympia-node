package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;

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

	class ProcedureResult {
		private final CMAction cmAction;
		private final Object output;

		public ProcedureResult(CMAction cmAction, Object output) {
			this.cmAction = cmAction;
			this.output = output;
		}

		public CMAction getCmAction() {
			return cmAction;
		}

		public Object getOutput() {
			return output;
		}
	}

	ProcedureResult execute(
		T inputParticle,
		U outputParticle,
		ProcedureResult prevResult
	);
}
