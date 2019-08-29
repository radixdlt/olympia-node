package com.radixdlt.atommodel.message;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Optional;

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
			new TransitionToken<>(VoidParticle.class, TypeToken.of(VoidUsedData.class), MessageParticle.class, TypeToken.of(VoidUsedData.class)),
			new TransitionProcedure<VoidParticle, VoidUsedData, MessageParticle, VoidUsedData>() {
				@Override
				public Result precondition(VoidParticle inputParticle, VoidUsedData inputUsed, MessageParticle outputParticle,
					VoidUsedData outputUsed) {
					return Result.success();
				}

				@Override
				public Optional<UsedData> inputUsed(VoidParticle inputParticle, VoidUsedData inputUsed, MessageParticle outputParticle,
					VoidUsedData outputUsed) {
					return Optional.empty();
				}

				@Override
				public WitnessValidator<VoidParticle> inputWitnessValidator() {
					return (i, w) -> WitnessValidatorResult.success();
				}

				@Override
				public Optional<UsedData> outputUsed(VoidParticle inputParticle, VoidUsedData inputUsed, MessageParticle outputParticle,
					VoidUsedData outputUsed) {
					return Optional.empty();
				}

				@Override
				public WitnessValidator<MessageParticle> outputWitnessValidator() {
					return (msg, meta) ->
						meta.isSignedBy(msg.getFrom().getKey())
							? WitnessValidatorResult.success()
							: WitnessValidatorResult.error("Message particle " + msg + " not signed.");
				}
			}
		);
	}
}
