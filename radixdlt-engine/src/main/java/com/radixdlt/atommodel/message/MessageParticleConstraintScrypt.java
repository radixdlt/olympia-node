/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.message;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
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
		os.registerParticle(MessageParticle.class, ParticleDefinition.<MessageParticle>builder()
			.addressMapper(MessageParticle::getAddresses)
			.staticValidation(MessageParticleConstraintScrypt::staticCheck)
			.build()
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
				public UsedCompute<VoidParticle, VoidUsedData, MessageParticle, VoidUsedData> inputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public UsedCompute<VoidParticle, VoidUsedData, MessageParticle, VoidUsedData> outputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public WitnessValidator<VoidParticle> inputWitnessValidator() {
					return (i, w) -> WitnessValidatorResult.success();
				}

				@Override
				public WitnessValidator<MessageParticle> outputWitnessValidator() {
					return (msg, meta) ->
						meta.isSignedBy(msg.getFrom().getPublicKey())
							? WitnessValidatorResult.success()
							: WitnessValidatorResult.error("Message particle " + msg + " not signed.");
				}
			}
		);
	}

	private static Result staticCheck(MessageParticle m) {
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
}
