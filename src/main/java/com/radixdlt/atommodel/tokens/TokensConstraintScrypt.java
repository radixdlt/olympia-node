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
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.RoutineCalls;
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
import com.radixdlt.utils.UIntUtils;

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
				.addressMapper(StakedTokensParticle::getAddresses)
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

	public static final class StakedAmount implements UsedData {
		private final UInt256 amount;

		StakedAmount(UInt256 usedAmount) {
			this.amount = Objects.requireNonNull(usedAmount);
		}

		public UInt256 getAmount() {
			return this.amount;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return TypeToken.of(StakedAmount.class);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof StakedAmount)) {
				return false;
			}
			StakedAmount that = (StakedAmount) o;
			return Objects.equals(amount, that.amount);
		}

		@Override
		public int hashCode() {
			return Objects.hash(amount);
		}

		@Override
		public String toString() {
			return String.valueOf(this.amount);
		}
	}

	private static final class ProvidedDelegate implements UsedData {
		private final RegisteredValidatorParticle delegate;

		private ProvidedDelegate(RegisteredValidatorParticle delegate) {
			this.delegate = delegate;
		}

		public RegisteredValidatorParticle getDelegate() {
			return delegate;
		}

		@Override
		public TypeToken<? extends UsedData> getTypeToken() {
			return TypeToken.of(ProvidedDelegate.class);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			ProvidedDelegate delegate1 = (ProvidedDelegate) o;
			return Objects.equals(delegate, delegate1.delegate);
		}

		@Override
		public int hashCode() {
			return Objects.hash(delegate);
		}
	}

	private static final class CreateStakingTransitionRoutine implements ConstraintRoutine {
		private final BiFunction<TransferrableTokensParticle, StakedTokensParticle, Result> transition;
		private final WitnessValidator<TransferrableTokensParticle> inputWitnessValidator;
		private final WitnessValidator<StakedTokensParticle> outputWitnessValidator;

		private CreateStakingTransitionRoutine(
			BiFunction<TransferrableTokensParticle, StakedTokensParticle, Result> transition,
			WitnessValidator<TransferrableTokensParticle> inputWitnessValidator,
			WitnessValidator<StakedTokensParticle> outputWitnessValidator
		) {
			this.transition = Objects.requireNonNull(transition);
			this.inputWitnessValidator = Objects.requireNonNull(inputWitnessValidator);
			this.outputWitnessValidator = Objects.requireNonNull(outputWitnessValidator);
		}

		@Override
		public void main(RoutineCalls calls) {
			// The staking transition works as follows (in order)
			// --- Format ---
			// Particle(props), input, output
			// -> Particle(props), next input, next output
			// --- ------ ---
			// 1x
			// RegisteredValidator(nonce), void, void
			// -> RegisteredValidator(nonce+1), ProvidedDelegate, none
			createCompanionTransition(calls);
			// 1x
			// RegisteredValidator(nonce+1), ProvidedDelegate, void
			// -> StakedTokens(amount), void, StakedAmount(amount)
			createStakeCheckTransition(calls);
			// nx
			// TransferrableTokens(transferred), void, StakedAmount
			// -> StakedTokens(remaining), [UsedAmount(remaining)],  [StakedAmount(remaining-transferred)]
			createStakeFundTransition(calls);
		}

		private void createCompanionTransition(RoutineCalls calls) {
			// 1x
			// RegisteredValidator(nonce), void, void
			// -> RegisteredValidator(nonce+1), ProvidedDelegate, none
			calls.createTransition(
				new TransitionToken<>(RegisteredValidatorParticle.class, TypeToken.of(VoidUsedData.class),
					RegisteredValidatorParticle.class, TypeToken.of(VoidUsedData.class)),
				new TransitionProcedure<RegisteredValidatorParticle, VoidUsedData, RegisteredValidatorParticle, VoidUsedData>() {
					@Override
					public Result precondition(RegisteredValidatorParticle inputParticle, VoidUsedData inputUsed,
					                           RegisteredValidatorParticle outputParticle, VoidUsedData outputUsed) {
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
					public UsedCompute<RegisteredValidatorParticle, VoidUsedData, RegisteredValidatorParticle, VoidUsedData> inputUsedCompute() {
						return (input, inputUsed, output, outputUsed) -> Optional.of(new ProvidedDelegate(output));
					}

					@Override
					public UsedCompute<RegisteredValidatorParticle, VoidUsedData, RegisteredValidatorParticle, VoidUsedData> outputUsedCompute() {
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
		}

		private void createStakeCheckTransition(RoutineCalls calls) {
			// 1x
			// RegisteredValidator(nonce+1), ProvidedDelegate, void
			// -> StakedTokens(amount), void, StakedAmount(amount)
			calls.createTransition(
				new TransitionToken<>(RegisteredValidatorParticle.class, TypeToken.of(ProvidedDelegate.class),
					StakedTokensParticle.class, TypeToken.of(VoidUsedData.class)),
				new TransitionProcedure<RegisteredValidatorParticle, ProvidedDelegate, StakedTokensParticle, VoidUsedData>() {
					@Override
					public Result precondition(RegisteredValidatorParticle inputParticle, ProvidedDelegate inputUsed,
					                           StakedTokensParticle outputParticle, VoidUsedData outputUsed) {
						// check that we're talking about the same validator
						if (!inputParticle.getAddress().equals(inputUsed.getDelegate().getAddress())) {
							return Result.error(String.format(
								"delegate address does not match used delegate address: %s != %s",
								inputParticle.getAddress(), inputUsed.getDelegate())
							);
						}

						// check that the validator (i.e. the delegate) allows the delegator to stake against it
						if (!inputUsed.getDelegate().allowsDelegator(outputParticle.getAddress())) {
							return Result.error(String.format(
								"delegate %s does not allow delegator %s",
								inputUsed.getDelegate(), outputParticle.getAddress())
							);
						}

						return Result.success();
					}

					@Override
					public UsedCompute<RegisteredValidatorParticle, ProvidedDelegate, StakedTokensParticle, VoidUsedData> inputUsedCompute() {
						return (inputParticle, inputUsed, outputParticle, outputUsed) -> Optional.empty();
					}

					@Override
					public UsedCompute<RegisteredValidatorParticle, ProvidedDelegate, StakedTokensParticle, VoidUsedData> outputUsedCompute() {
						return (inputParticle, inputUsed, outputParticle, outputUsed)
							-> Optional.of(new StakedAmount(outputParticle.getAmount()));
					}

					@Override
					public WitnessValidator<RegisteredValidatorParticle> inputWitnessValidator() {
						return (p, witnessData) -> WitnessValidatorResult.success();
					}

					@Override
					public WitnessValidator<StakedTokensParticle> outputWitnessValidator() {
						return outputWitnessValidator;
					}
				}
			);
		}

		private void createStakeFundTransition(RoutineCalls calls) {
			// nx
			// TransferrableTokens(transferred), void, StakedAmount
			// -> StakedTokens(remaining), [UsedAmount(remaining)],  [StakedAmount(remaining-transferred)]
			calls.createTransition(
				new TransitionToken<>(TransferrableTokensParticle.class, TypeToken.of(VoidUsedData.class),
					StakedTokensParticle.class, TypeToken.of(StakedAmount.class)),
				new TransitionProcedure<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount>() {
					@Override
					public Result precondition(TransferrableTokensParticle inputParticle, VoidUsedData inputUsed,
					                           StakedTokensParticle outputParticle, StakedAmount outputUsed) {
						return transition.apply(inputParticle, outputParticle);
					}

					@Override
					public UsedCompute<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount> inputUsedCompute() {
						return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
							final UInt256 inputAmount = inputParticle.getAmount();
							final UInt256 remainingOutputAmount = outputUsed.getAmount();
							int compare = inputAmount.compareTo(remainingOutputAmount);
							if (compare > 0) {
								return Optional.of(new CreateFungibleTransitionRoutine.UsedAmount(remainingOutputAmount));
							} else {
								return Optional.empty();
							}
						};
					}

					@Override
					public UsedCompute<TransferrableTokensParticle, VoidUsedData, StakedTokensParticle, StakedAmount> outputUsedCompute() {
						return (inputParticle, inputUsed, outputParticle, outputUsed) -> {
							final UInt256 inputAmount = inputParticle.getAmount();
							final UInt256 remainingOutputAmount = outputUsed.getAmount();
							int compare = inputAmount.compareTo(remainingOutputAmount);
							if (compare < 0) {
								UInt256 remainingStakedAmount = UIntUtils.subtractWithUnderflow(remainingOutputAmount, inputAmount);
								return Optional.of(new StakedAmount(remainingStakedAmount));
							} else {
								return Optional.empty();
							}
						};
					}

					@Override
					public WitnessValidator<TransferrableTokensParticle> inputWitnessValidator() {
						return inputWitnessValidator;
					}

					@Override
					public WitnessValidator<StakedTokensParticle> outputWitnessValidator() {
						return outputWitnessValidator;
					}
				}
			);
		}
	}

	private void defineStaking(SysCalls os) {
		// Staking
		os.executeRoutine(new CreateStakingTransitionRoutine(
			checkEquals(
				TransferrableTokensParticle::getGranularity,
				StakedTokensParticle::getGranularity,
				"Granularities not equal.",
				TransferrableTokensParticle::getTokenPermissions,
				StakedTokensParticle::getTokenPermissions,
				"Permissions not equal."
			),
			(in, meta) -> checkSignedBy(meta, in.getAddress()),
			(out, meta) -> checkSignedBy(meta, out.getAddress())
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

		// Stake movement
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			StakedTokensParticle.class,
			StakedTokensParticle::getAmount,
			StakedTokensParticle::getAmount,
			checkEquals(
				StakedTokensParticle::getGranularity,
				StakedTokensParticle::getGranularity,
				"Granularities not equal.",
				StakedTokensParticle::getTokenPermissions,
				StakedTokensParticle::getTokenPermissions,
				"Permissions not equal.",
				StakedTokensParticle::getAddress,
				StakedTokensParticle::getAddress,
				"Can't send staked tokens to another address."
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

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0,
		Function<L, R1> leftMapper1, Function<R, R1> rightMapper1, String errorMessage1
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0)
			.mapSuccess(() -> Result.of(Objects.equals(leftMapper1.apply(l), rightMapper1.apply(r)), errorMessage1));
	}

	private static <L, R, R0, R1, R2> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0,
		Function<L, R1> leftMapper1, Function<R, R1> rightMapper1, String errorMessage1,
		Function<L, R2> leftMapper2, Function<R, R2> rightMapper2, String errorMessage2
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0)
			.mapSuccess(() -> Result.of(Objects.equals(leftMapper1.apply(l), rightMapper1.apply(r)), errorMessage1))
			.mapSuccess(() -> Result.of(Objects.equals(leftMapper2.apply(l), rightMapper2.apply(r)), errorMessage2));
	}
}
