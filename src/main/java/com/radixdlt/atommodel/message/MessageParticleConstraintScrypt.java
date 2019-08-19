package com.radixdlt.atommodel.message;

import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;

/**
 * Scrypt which defines the constraints on the message particle
 */
public class MessageParticleConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticleMultipleAddresses(
			MessageParticle.class,
			MessageParticle::getAddresses,
			m -> {
				if (m.getBytes() == null) {
					return Result.error("message data is null");
				}

				if (m.getFrom() == null) {
					return Result.error("from is null");
				}

				if (m.getTo() == null) {
					return Result.error("to is null");
				}

				return Result.success();
			}
		);

		os.createTransition(
			null,
			MessageParticle.class,
			(in, usedIn, out, usedOut) -> ProcedureResult.popOutput(null),
			(res, in, out, meta) -> meta.isSignedBy(out.getFrom())
				? WitnessValidatorResult.success()
				: WitnessValidatorResult.error("Message particle " + out + " not signed.")
		);
	}
}
