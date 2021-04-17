/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.system;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.store.ImmutableIndex;

import java.util.Optional;

/**
 * Allows for the update of the epoch, timestamp and view state.
 * Currently there is only a single system particle that should be in
 * existence.
 * TODO: use a non-radix-address path to store this system info
 */
public final class SystemConstraintScrypt implements ConstraintScrypt {

	public SystemConstraintScrypt() {
		// Nothing here right now
	}

	private Result staticCheck(SystemParticle systemParticle) {
		if (systemParticle.getEpoch() < 0) {
			return Result.error("Epoch is less than 0");
		}

		if (systemParticle.getTimestamp() < 0) {
			return Result.error("Timestamp is less than 0");
		}

		if (systemParticle.getView() < 0) {
			return Result.error("View is less than 0");
		}

		// FIXME: Need to validate view, but need additional state to do that successfully

		return Result.success();
	}

	@Override
	public void main(SysCalls os) {
		os.registerParticle(SystemParticle.class, ParticleDefinition.<SystemParticle>builder()
			.staticValidation(this::staticCheck)
			.virtualizeUp(p -> p.getView() == 0 && p.getEpoch() == 0 && p.getTimestamp() == 0)
			.build()
		);

		os.createTransition(
			new TransitionToken<>(
				SystemParticle.class,
				SystemParticle.class,
				TypeToken.of(VoidReducerState.class)
			),

			new TransitionProcedure<>() {
				@Override
				public PermissionLevel requiredPermissionLevel(SystemParticle i, SystemParticle o, ImmutableIndex index) {
					return PermissionLevel.SUPER_USER;
				}

				@Override
				public Result precondition(
					SystemParticle inputParticle,
					SystemParticle outputParticle,
					VoidReducerState outputUsed,
					ImmutableIndex immutableIndex
				) {

					if (inputParticle.getEpoch() == outputParticle.getEpoch()) {
						if (inputParticle.getView() >= outputParticle.getView()) {
							return Result.error("Next view must be greater than previous.");
						}
					} else if (inputParticle.getEpoch() + 1 != outputParticle.getEpoch()) {
						return Result.error("Bad next epoch");
					} else if (outputParticle.getView() != 0) {
						return Result.error("Change of epochs must start with view 0.");
					}

					return Result.success();
				}

				@Override
				public InputOutputReducer<SystemParticle, SystemParticle, VoidReducerState> inputOutputReducer() {
					return (input, output, index, outputUsed) -> ReducerResult.complete(
						input.getEpoch() == output.getEpoch()
							? new SystemNextView(output.getView(), output.getTimestamp(), input.getEpoch())
							: new SystemNextEpoch(output.getTimestamp(), output.getEpoch())
					);
				}

				@Override
				public SignatureValidator<SystemParticle, SystemParticle> signatureRequired() {
					return (i, o, index) -> Optional.empty();
				}
			}
		);
	}
}
