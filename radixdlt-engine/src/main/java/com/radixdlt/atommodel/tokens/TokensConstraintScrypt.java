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
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedCompute;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidUsedData;
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
	}

	private void registerParticles(SysCalls os) {
		os.registerParticle(
			MutableSupplyTokenDefinitionParticle.class,
			ParticleDefinition.<MutableSupplyTokenDefinitionParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(MutableSupplyTokenDefinitionParticle::getRRI)
				.build()
		);

		os.registerParticle(
			FixedSupplyTokenDefinitionParticle.class,
			ParticleDefinition.<FixedSupplyTokenDefinitionParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(FixedSupplyTokenDefinitionParticle::getRRI)
				.build()
		);

		os.registerParticle(
			UnallocatedTokensParticle.class,
			ParticleDefinition.<UnallocatedTokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(UnallocatedTokensParticle::getTokDefRef)
				.build()
		);

		os.registerParticle(
			TransferrableTokensParticle.class,
			ParticleDefinition.<TransferrableTokensParticle>builder()
				.allowTransitionsFromOutsideScrypts()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TransferrableTokensParticle::getTokDefRef)
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
			(i, o) -> Result.success(),
			i -> Optional.of(i.getTokDefRef().getAddress().getPublicKey())
		));
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			UnallocatedTokensParticle.class,
			TransferrableTokensParticle.class,
			UnallocatedTokensParticle::getAmount,
			TransferrableTokensParticle::getAmount,
			(i, o) -> {
				if (!o.isBurnable()) {
					return Result.error("Output is not mutable");
				}

				return Result.success();
			},
			i -> Optional.of(i.getTokDefRef().getAddress().getPublicKey())
		));

		// Transfers
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TransferrableTokensParticle.class,
			TransferrableTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			TransferrableTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::isBurnable,
				TransferrableTokensParticle::isBurnable,
				"Permissions not equal."
			),
			i -> Optional.of(i.getAddress().getPublicKey())
		));


		// Burns
		os.executeRoutine(new ConstraintRoutine() {
			@Override
			public void main(RoutineCalls calls) {
				calls.createTransition(
					new TransitionToken<>(
						TransferrableTokensParticle.class,
						TypeToken.of(CreateFungibleTransitionRoutine.UsedAmount.class),
						VoidParticle.class,
						TypeToken.of(VoidUsedData.class)
					),
					new TransitionProcedure<>() {
						@Override
						public Result precondition(TransferrableTokensParticle inputParticle, CreateFungibleTransitionRoutine.UsedAmount inputUsed, VoidParticle outputParticle, VoidUsedData outputUsed) {
							if (!inputParticle.isBurnable()) {
								return Result.error("Cannot burn token.");
							}

							return Result.success();
						}

						@Override
						public UsedCompute<TransferrableTokensParticle, CreateFungibleTransitionRoutine.UsedAmount, VoidParticle, VoidUsedData> inputUsedCompute() {
							return (inputParticle, inputUsed, outputParticle, outputUsed) -> Optional.empty();
						}

						@Override
						public UsedCompute<TransferrableTokensParticle, CreateFungibleTransitionRoutine.UsedAmount, VoidParticle, VoidUsedData> outputUsedCompute() {
							return (inputParticle, inputUsed, outputParticle, outputUsed) -> Optional.empty();
						}

						@Override
						public SignatureValidator<TransferrableTokensParticle> inputSignatureRequired() {
							return i -> Optional.of(i.getAddress().getPublicKey());
						}
					}
				);
			}
		});
	}

	@VisibleForTesting
	static Result checkCreateTransferrable(FixedSupplyTokenDefinitionParticle tokDef, TransferrableTokensParticle transferrable) {
		if (!Objects.equals(tokDef.getSupply(), transferrable.getAmount())) {
			return Result.error("Supply and amount are not equal.");
		}

		if (transferrable.isBurnable()) {
			return Result.error("Tokens must be non-mutable.");
		}

		return Result.success();
	}

	@VisibleForTesting
	static Result checkCreateUnallocated(MutableSupplyTokenDefinitionParticle tokDef, UnallocatedTokensParticle unallocated) {
		if (!unallocated.getAmount().equals(UInt256.MAX_VALUE)) {
			return Result.error("Unallocated amount must be UInt256.MAX_VALUE but was " + unallocated.getAmount());
		}

		return Result.success();
	}

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0);
	}

}
