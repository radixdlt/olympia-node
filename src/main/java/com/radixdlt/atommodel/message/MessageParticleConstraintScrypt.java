package com.radixdlt.atommodel.message;

import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.OutputProcedure.OutputProcedureResult;
import com.radixdlt.constraintmachine.OutputWitnessValidator.OutputWitnessValidatorResult;

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

		os.createOutputOnlyTransition(
			MessageParticle.class,
			out -> OutputProcedureResult.pop(),
			(out, meta) -> meta.isSignedBy(out.getFrom().getKey())
				? OutputWitnessValidatorResult.success()
				: OutputWitnessValidatorResult.error("Message particle " + out + " not signed.")
		);
	}
}
