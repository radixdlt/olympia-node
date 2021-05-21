/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.atommodel.system;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.UInt256;

public class SystemConstraintScryptV2 implements ConstraintScrypt {

	public static final UInt256 REWARDS_PER_PROPOSAL = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private static class Inflation implements ReducerState {
		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(Inflation.class);
		}
	}


	public SystemConstraintScryptV2() {
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

		os.registerParticle(
			Stake.class,
			ParticleDefinition.<Stake>builder()
				.rriMapper(p -> REAddr.ofNativeToken())
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
				public PermissionLevel requiredPermissionLevel(
					SubstateWithArg<SystemParticle> i,
					SystemParticle o,
					ReadableAddrs index
				) {
					return PermissionLevel.SUPER_USER;
				}

				@Override
				public Result precondition(
					SubstateWithArg<SystemParticle> in,
					SystemParticle outputParticle,
					VoidReducerState outputUsed,
					ReadableAddrs readableAddrs
				) {
					if (in.getArg().isPresent()) {
						return Result.error("No arguments allowed");
					}

					var inputParticle = in.getSubstate();
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
					return (input, output, index, outputUsed) -> input.getSubstate().getEpoch() != output.getEpoch()
						? ReducerResult.complete(new SystemNextEpoch(output.getTimestamp()))
						: ReducerResult.incomplete(new Inflation(), true);
				}

				@Override
				public SignatureValidator<SystemParticle, SystemParticle> signatureValidator() {
					return (i, o, index, pubKey) -> pubKey.isEmpty(); // Must not be signed
				}
			}
		);

		os.createTransition(
			new TransitionToken<>(
				SystemParticle.class,
				Stake.class,
				TypeToken.of(Inflation.class)
			),
			new TransitionProcedure<>() {
				@Override
				public Result precondition(
					SubstateWithArg<SystemParticle> in,
					Stake output,
					Inflation inputUsed,
					ReadableAddrs readableAddrs
				) {
					if (!output.getAmount().equals(REWARDS_PER_PROPOSAL)) {
						return Result.error("Rewards must be " + REWARDS_PER_PROPOSAL);
					}
					return Result.success();
				}

				@Override
				public PermissionLevel requiredPermissionLevel(
					SubstateWithArg<SystemParticle> i, Stake o, ReadableAddrs index
				) {
					return PermissionLevel.SUPER_USER;
				}

				@Override
				public InputOutputReducer<SystemParticle, Stake, Inflation> inputOutputReducer() {
					return (i, o, index, outputUsed) -> ReducerResult.complete(
						Unknown.create()
					);
				}

				@Override
				public SignatureValidator<SystemParticle, Stake> signatureValidator() {
					return (i, o, index, publicKey) -> publicKey.isEmpty(); // Must not be signed
				}
			});
	}
}
