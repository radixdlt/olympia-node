package com.radixdlt.atommodel.validators;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintScrypt;
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

public class ValidatorConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticleMultipleAddresses(
			UnregisteredValidatorParticle.class,
			p -> ImmutableSet.of(p.getAddress()),
			createStaticCheck(UnregisteredValidatorParticle::getAddress),
			null,
			p -> p.getNonce() == 0 ? Spin.UP : null // virtualise first instance as UP
		);

		os.registerParticle(
			RegisteredValidatorParticle.class,
			RegisteredValidatorParticle::getAddress,
			createStaticCheck(RegisteredValidatorParticle::getAddress)
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

	private <I extends Particle, O extends Particle> void createTransition(SysCalls os,
	                                                                       Class<I> inputParticle,
	                                                                       Function<I, RadixAddress> inputAddressMapper,
	                                                                       ToLongFunction<I> inputNonceMapper,
	                                                                       Class<O> outputParticle,
	                                                                       Function<O, RadixAddress> outputAddressMapper,
	                                                                       ToLongFunction<O> outputNonceMapper
	) {
		os.createTransition(
			new TransitionToken<>(inputParticle, TypeToken.of(VoidUsedData.class), outputParticle, TypeToken.of(VoidUsedData.class)),
			new TransitionProcedure<I, VoidUsedData, O, VoidUsedData>() {
				@Override
				public Result precondition(I inputParticle, VoidUsedData inputUsed, O outputParticle, VoidUsedData outputUsed) {
					RadixAddress inputAddress = inputAddressMapper.apply(inputParticle);
					RadixAddress outputAddress = outputAddressMapper.apply(outputParticle);
					if (!Objects.equals(inputAddress, outputAddressMapper.apply(outputParticle))) {
						return Result.error(String.format(
							"validator addresses do not match: %s != %s",
							inputAddress, outputAddress
						));
					}

					long inputNonce = inputNonceMapper.applyAsLong(inputParticle);
					long outputNonce = outputNonceMapper.applyAsLong(outputParticle);
					if (inputNonce + 1 != outputNonce) {
						return Result.error(String.format(
							"output nonce must be input nonce + 1, but %d != %d + 1",
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
		);
	}


	private static <I> Function<I, Result> createStaticCheck(Function<I, RadixAddress> addressMapper) {
		return particle -> {
			if (addressMapper.apply(particle) == null) {
				return Result.error("address is null");
			}

			return Result.success();
		};
	}
}
