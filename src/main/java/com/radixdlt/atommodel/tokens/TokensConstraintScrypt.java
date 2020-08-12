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

package com.radixdlt.atommodel.tokens;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Scrypt which defines how tokens are managed.
 */
public class TokensConstraintScrypt implements ConstraintScrypt {

	@Override
	public void main(SysCalls os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
		defineStaking(os);
	}

	private void registerParticles(SysCalls os) {
		os.registerParticle(
			MutableSupplyTokenDefinitionParticle.class,
			ParticleDefinition.<MutableSupplyTokenDefinitionParticle>builder()
				.singleAddressMapper(p -> p.getRRI().getAddress())
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(MutableSupplyTokenDefinitionParticle::getRRI)
				.build()
		);

		os.registerParticle(
			FixedSupplyTokenDefinitionParticle.class,
			ParticleDefinition.<FixedSupplyTokenDefinitionParticle>builder()
				.singleAddressMapper(p -> p.getRRI().getAddress())
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(FixedSupplyTokenDefinitionParticle::getRRI)
				.build()
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			ParticleDefinition.<UnallocatedTokensParticle>builder()
				.singleAddressMapper(UnallocatedTokensParticle::getAddress)
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(UnallocatedTokensParticle::getTokDefRef)
				.build()
		);

		os.registerParticle(
			TransferrableTokensParticle.class,
			ParticleDefinition.<TransferrableTokensParticle>builder()
				.singleAddressMapper(TransferrableTokensParticle::getAddress)
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TransferrableTokensParticle::getTokDefRef)
				.build()
		);

		os.registerParticle(
			StakedTokensParticle.class,
			ParticleDefinition.<StakedTokensParticle>builder()
				.singleAddressMapper(StakedTokensParticle::getAddress)
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(StakedTokensParticle::getTokDefRef)
				.build()
		);
	}

	private void defineTokenCreation(SysCalls os) {
		// Require Token Definition to be created with unallocated tokens of max supply
		os.createTransitionFromRRICombined(
			MutableSupplyTokenDefinitionParticle.class,
			UnallocatedTokensParticle.class,
			TokensConstraintScrypt::checkCreateUnallocated
		);

		os.createTransitionFromRRICombined(
			FixedSupplyTokenDefinitionParticle.class,
			TransferrableTokensParticle.class,
			TokensConstraintScrypt::checkCreateTransferrable
		);

		// Unallocated movement
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle.class,
			UnallocatedTokensParticle::getAmount,
			UnallocatedTokensParticle::getAmount,
			checkEquals(
				UnallocatedTokensParticle::getGranularity,
				UnallocatedTokensParticle::getGranularity,
				"Granularities not equal.",
				UnallocatedTokensParticle::getTokenPermissions,
				UnallocatedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> meta.isSignedBy(in.getTokDefRef().getAddress().getPublicKey())
				? WitnessValidatorResult.success() : WitnessValidatorResult.error("Permission not allowed.")
		));
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			UnallocatedTokensParticle.class,
			TransferrableTokensParticle.class,
			UnallocatedTokensParticle::getAmount,
			TransferrableTokensParticle::getAmount,
			checkEquals(
				UnallocatedTokensParticle::getGranularity,
				TransferrableTokensParticle::getGranularity,
				"Granularities not equal.",
				UnallocatedTokensParticle::getTokenPermissions,
				TransferrableTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkTokenActionAllowed(meta, in.getTokenPermission(TokenTransition.MINT), in.getTokDefRef())
		));

		// Transfers
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			TransferrableTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				TransferrableTokensParticle::getGranularity,
				"Granularities not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				TransferrableTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkSignedBy(meta, in.getAddress())
		));

		// Burns
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TransferrableTokensParticle.class,
			UnallocatedTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			UnallocatedTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				UnallocatedTokensParticle::getGranularity,
				"Granularities not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				UnallocatedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkTokenActionAllowed(meta, in.getTokenPermission(TokenTransition.BURN), in.getTokDefRef())
		));
	}

	private static final class StakedAmount implements UsedData {
		private final UInt256 amount;
		private final RadixAddress delegator;
		private final RadixAddress delegate;

		private StakedAmount(UInt256 amount, RadixAddress delegator, RadixAddress delegate) {
			this.amount = amount;
			this.delegator = delegator;
			this.delegate = delegate;
		}

		private UInt256 getAmount() {
			return amount;
		}

		private RadixAddress getDelegate() {
			return delegate;
		}

		private RadixAddress getDelegator() {
			return delegator;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return TypeToken.of(StakedAmount.class);
		}

		@Override
		public int hashCode() {
			return Objects.hash(amount, delegator, delegate);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			StakedAmount that = (StakedAmount) o;
			return Objects.equals(amount, that.amount) &&
				Objects.equals(delegator, that.delegator) &&
				Objects.equals(delegate, that.delegate);
		}
	}

	private void defineStaking(SysCalls os) {
		// Staking main transition
		os.createTransition(
			new TransitionToken<>(TransferrableTokensParticle.class, TypeToken.of(VoidUsedData.class), StakedTokensParticle.class, TypeToken.of(StakedAmount.class)),
			new TransitionProcedure<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount>() {
				@Override
				public Result precondition(TransferrableTokensParticle inputParticle, VoidUsedData inputUsed, StakedTokensParticle outputParticle, StakedAmount outputUsed) {
					// TODO @Incomplete: do we need a precondition here?
					return Result.success();
				}

				@Override
				public UsedCompute<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount> inputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public UsedCompute<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount> outputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> {
						UInt256 totalInput = input.getAmount().add(outputUsed.getAmount());
						int compare = totalInput.compareTo(outputUsed.getAmount());
						if (compare >= 0) {
							return Optional.empty();
						} else {
							return Optional.of(new StakedAmount(totalInput, output.getAddress(), output.getDelegateAddress()));
						}
					};
				}

				@Override
				public WitnessValidator<TransferrableTokensParticle> inputWitnessValidator() {
					return (input, witnessData) -> witnessData.isSignedBy(input.getAddress().getPublicKey())
						? WitnessValidatorResult.success() : WitnessValidatorResult.error("Not signed by " + input.getAddress());
				}

				@Override
				public WitnessValidator<StakedTokensParticle> outputWitnessValidator() {
					return (o, witnessData) -> WitnessValidatorResult.success();
				}
			}
		);
		// Staking sidecar transition checking against the corresponding validator registration
		os.createTransition(
			new TransitionToken<>(RegisteredValidatorParticle.class, TypeToken.of(StakedAmount.class), RegisteredValidatorParticle.class, TypeToken.of(VoidUsedData.class)),
			new TransitionProcedure<RegisteredValidatorParticle, StakedAmount, RegisteredValidatorParticle, VoidUsedData>() {
				@Override
				public Result precondition(RegisteredValidatorParticle inputParticle, StakedAmount inputUsed, RegisteredValidatorParticle outputParticle, VoidUsedData outputUsed) {
					// check that we're talking about the same validator
					if (!inputParticle.getAddress().equals(inputUsed.getDelegate())) {
						return Result.error(String.format(
							"input validator address does not match used delegate address: %s != %s",
							inputParticle.getAddress(), inputUsed.getDelegate())
						);
					}

					// check that the validator (i.e. the delegate) allows the delegator to stake against it
					if (!inputParticle.allowsDelegator(inputUsed.getDelegator())) {
						return Result.error(String.format(
							"input validator %s does not allow used delegator address %s",
							inputParticle.getAddress(), inputUsed.getDelegator())
						);
					}

					// check that the registered validator particle is unmodified
					if (!inputParticle.equalsIgnoringNonce(outputParticle)) {
						return Result.error(String.format(
							"input validator and output validator do not match: %s != %s",
							inputParticle, outputParticle
						));
					}
					// .. and that the nonce has increased by exactly one
					if ((inputParticle.getNonce() + 1) != outputParticle.getNonce()) {
						return Result.error(String.format(
							"validator nonce must increase by exactly one but: %d + 1 != %d",
							inputParticle.getNonce(), outputParticle.getNonce()
						));
					}

					return Result.success();
				}

				@Override
				public UsedCompute<RegisteredValidatorParticle, StakedAmount, RegisteredValidatorParticle, VoidUsedData> inputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public UsedCompute<RegisteredValidatorParticle, StakedAmount, RegisteredValidatorParticle, VoidUsedData> outputUsedCompute() {
					return (input, inputUsed, output, outputUsed) -> Optional.empty();
				}

				@Override
				public WitnessValidator<RegisteredValidatorParticle> inputWitnessValidator() {
					return (o, witnessData) -> WitnessValidatorResult.success();
				}

				@Override
				public WitnessValidator<RegisteredValidatorParticle> outputWitnessValidator() {
					return (o, witnessData) -> WitnessValidatorResult.success();
				}
			}
		);

		// Staking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TransferrableTokensParticle.class,
			StakedTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			StakedTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				StakedTokensParticle::getGranularity,
				"Granularities not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				StakedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkSignedBy(meta, in.getAddress())
		));



		// Unstaking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			TransferrableTokensParticle.class,
			StakedTokensParticle::getAmount,
			TransferrableTokensParticle::getAmount,
			checkEquals(
				StakedTokensParticle::getGranularity,
				TransferrableTokensParticle::getGranularity,
				"Granularities not equal.",
				StakedTokensParticle::getTokenPermissions,
				TransferrableTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkSignedBy(meta, in.getAddress())
		));
	}

	@VisibleForTesting
	static Result checkCreateTransferrable(FixedSupplyTokenDefinitionParticle tokDef, TransferrableTokensParticle transferrable) {
		if (!Objects.equals(tokDef.getGranularity(), transferrable.getGranularity())) {
			return Result.error("Granularities not equal.");
		}
		if (!Objects.equals(tokDef.getSupply(), transferrable.getAmount())) {
			return Result.error("Supply and amount are not equal.");
		}
		if (!transferrable.getTokenPermissions().isEmpty()) {
			return Result.error("Transferrable tokens of a fixed supply token must be empty.");
		}

		return Result.success();
	}

	@VisibleForTesting
	static Result checkCreateUnallocated(MutableSupplyTokenDefinitionParticle tokDef, UnallocatedTokensParticle unallocated) {
		if (!Objects.equals(unallocated.getGranularity(), tokDef.getGranularity())) {
			return Result.error("Granularities not equal.");
		}
		if (!Objects.equals(unallocated.getTokenPermissions(), tokDef.getTokenPermissions())) {
			return Result.error("Permissions not equal.");
		}
		if (!unallocated.getAmount().equals(UInt256.MAX_VALUE)) {
			return Result.error("Unallocated amount must be UInt256.MAX_VALUE but was " + unallocated.getAmount());
		}

		return Result.success();
	}

	@VisibleForTesting
	static WitnessValidatorResult checkTokenActionAllowed(WitnessData meta, TokenPermission tokenPermission, RRI tokDefRef) {
		return tokenPermission.check(tokDefRef, meta).isSuccess()
			? WitnessValidatorResult.success() : WitnessValidatorResult.error("Permission not allowed.");
	}

	@VisibleForTesting
	static WitnessValidatorResult checkSignedBy(WitnessData meta, RadixAddress address) {
		return meta.isSignedBy(address.getPublicKey())
			? WitnessValidatorResult.success()
			: WitnessValidatorResult.error(String.format("Not signed by: %s", address.getPublicKey()));
	}

	private static <T, U, V> BiFunction<T, U, Result> checkEquals(
		Function<T, V> firstMapper0, Function<U, V> firstMapper1, String firstErrorMessage,
		Function<T, V> secondMapper0, Function<U, V> secondMapper1, String secondErrorMessage
	) {
		return (t, u) -> {
			if (!Objects.equals(firstMapper0.apply(t), firstMapper1.apply(u))) {
				return Result.error(firstErrorMessage);
			}

			if (!Objects.equals(secondMapper0.apply(t), secondMapper1.apply(u))) {
				return Result.error(secondErrorMessage);
			}

			return Result.success();
		};
	}
}
