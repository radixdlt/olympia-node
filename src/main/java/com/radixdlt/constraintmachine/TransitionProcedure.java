package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.Particle;
import java.util.concurrent.atomic.AtomicReference;

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
		public ProcedureResult(CMAction cmAction) {
			this.cmAction = cmAction;
		}

		public CMAction getCmAction() {
			return cmAction;
		}
	}

	ProcedureResult execute(
		T inputParticle,
		U outputParticle,
		AtomicReference<Object> data,
		ProcedureResult prevResult
	);
}
