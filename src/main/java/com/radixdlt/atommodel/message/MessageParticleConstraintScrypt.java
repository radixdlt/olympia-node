package com.radixdlt.atommodel.message;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidUsedData;
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
			new TransitionToken<>(
				VoidParticle.class,
				TypeToken.of(VoidUsedData.class),
				MessageParticle.class,
				TypeToken.of(VoidUsedData.class)
			),
			(in, usedIn, out, usedOut) -> ProcedureResult.popOutput(null, (msg, meta) ->
				meta.isSignedBy(msg.getFrom().getKey())
					? WitnessValidatorResult.success()
					: WitnessValidatorResult.error("Message particle " + msg + " not signed.")
			)
		);
	}
}
