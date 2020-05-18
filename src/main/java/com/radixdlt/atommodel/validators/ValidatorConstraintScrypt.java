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

package com.radixdlt.atommodel.validators;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Constraint Scrypt defining the Validator FSM, specifically the registration/unregistration flow.
 * <p>
 * The Validator FSM is implemented with two particles, UnregisteredValidatorParticle and RegisteredValidatorParticle,
 * both carrying the address of the validator in question and a nonce. The first unregistered Validator particle
 * for an address (with nonce 0) is virtualised as having an UP spin to initialise the FSM. Whenever a validator
 * (identified by their address) transitions between the two states, the nonce must increase (to ensure uniqueness).
 * The atom carrying a transition must be signed by the validator.
 */
public class ValidatorConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(UnregisteredValidatorParticle.class, ParticleDefinition.<UnregisteredValidatorParticle>builder()
			.singleAddressMapper(UnregisteredValidatorParticle::getAddress)
			.staticValidation(createStaticCheck(UnregisteredValidatorParticle::getAddress))
			.virtualizeSpin(p -> p.getNonce() == 0 ? Spin.UP : null) // virtualize first instance as UP
			.build()
		);

		os.registerParticle(RegisteredValidatorParticle.class, ParticleDefinition.<RegisteredValidatorParticle>builder()
			.singleAddressMapper(RegisteredValidatorParticle::getAddress)
			.staticValidation(createStaticCheck(RegisteredValidatorParticle::getAddress))
			.build()
		);

		// transition from unregistered => registered
		createTransition(os,
			UnregisteredValidatorParticle.class,
			UnregisteredValidatorParticle::getAddress,
			UnregisteredValidatorParticle::getNonce,
			RegisteredValidatorParticle.class,
			RegisteredValidatorParticle::getAddress,
			RegisteredValidatorParticle::getNonce);

		// transition from registered => unregistered
		createTransition(os,
			RegisteredValidatorParticle.class,
			RegisteredValidatorParticle::getAddress,
			RegisteredValidatorParticle::getNonce,
			UnregisteredValidatorParticle.class,
			UnregisteredValidatorParticle::getAddress,
			UnregisteredValidatorParticle::getNonce);
	}

	private <I extends Particle, O extends Particle> void createTransition(
		SysCalls os,
		Class<I> inputParticle,
		Function<I, RadixAddress> inputAddressMapper,
		ToLongFunction<I> inputNonceMapper,
		Class<O> outputParticle,
		Function<O, RadixAddress> outputAddressMapper,
		ToLongFunction<O> outputNonceMapper
	) {
		os.createTransition(
			new TransitionToken<>(inputParticle, TypeToken.of(VoidUsedData.class), outputParticle, TypeToken.of(VoidUsedData.class)),
			new ValidatorTransitionProcedure<>(inputAddressMapper, inputNonceMapper, outputAddressMapper, outputNonceMapper)
		);
	}

	// create a check verifying that the given address doesn't map to null
	private static <I> Function<I, Result> createStaticCheck(Function<I, RadixAddress> addressMapper) {
		return particle -> {
			if (addressMapper.apply(particle) == null) {
				return Result.error("address is null");
			}

			return Result.success();
		};
	}

	@VisibleForTesting
	static class ValidatorTransitionProcedure<I extends Particle, O extends Particle>
		implements TransitionProcedure<I, VoidUsedData, O, VoidUsedData> {
		private final Function<I, RadixAddress> inputAddressMapper;
		private final ToLongFunction<I> inputNonceMapper;
		private final Function<O, RadixAddress> outputAddressMapper;
		private final ToLongFunction<O> outputNonceMapper;

		ValidatorTransitionProcedure(
			Function<I, RadixAddress> inputAddressMapper,
			ToLongFunction<I> inputNonceMapper,
			Function<O, RadixAddress> outputAddressMapper,
			ToLongFunction<O> outputNonceMapper
		) {
			this.inputAddressMapper = inputAddressMapper;
			this.inputNonceMapper = inputNonceMapper;
			this.outputAddressMapper = outputAddressMapper;
			this.outputNonceMapper = outputNonceMapper;
		}

		@Override
		public Result precondition(I inputParticle, VoidUsedData inputUsed, O outputParticle, VoidUsedData outputUsed) {
			RadixAddress inputAddress = inputAddressMapper.apply(inputParticle);
			RadixAddress outputAddress = outputAddressMapper.apply(outputParticle);
			// ensure transition is between validator particles concerning the same validator address
			if (!Objects.equals(inputAddress, outputAddress)) {
				return Result.error(String.format(
					"validator addresses do not match: %s != %s",
					inputAddress, outputAddress
				));
			}

			// ensure nonce is increased when transitioning between the two states
			long inputNonce = inputNonceMapper.applyAsLong(inputParticle);
			long outputNonce = outputNonceMapper.applyAsLong(outputParticle);
			if (inputNonce + 1 != outputNonce) {
				return Result.error(String.format(
					"output nonce must be input nonce + 1, but %s != %s + 1",
					outputNonce, inputNonce
				));
			}

			return Result.success();
		}

		@Override
		public UsedCompute<I, VoidUsedData, O, VoidUsedData> inputUsedCompute() {
			return (input, inputUsed, output, outputUsed) -> Optional.empty();
		}

		@Override
		public UsedCompute<I, VoidUsedData, O, VoidUsedData> outputUsedCompute() {
			return (input, inputUsed, output, outputUsed) -> Optional.empty();
		}

		@Override
		public WitnessValidator<I> inputWitnessValidator() {
			// verify that the transition was authenticated by the validator address in question
			return (i, meta) -> {
				RadixAddress address = inputAddressMapper.apply(i);
				return meta.isSignedBy(address.getPublicKey())
					? WitnessValidatorResult.success()
					: WitnessValidatorResult.error(String.format("validator %s not signed", address));
			};
		}

		@Override
		public WitnessValidator<O> outputWitnessValidator() {
			// input.address == output.address, so no need to check signature twice
			return (i, meta) -> WitnessValidatorResult.success();
		}
	}
}
