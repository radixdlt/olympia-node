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
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.constraintmachine.ReadOnlyData;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine.UsedAmount;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.VoidParticle;

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
			TokenDefinitionParticle.class,
			ParticleDefinition.<TokenDefinitionParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TokenDefinitionParticle::getRRI)
				.build()
		);

		os.registerParticle(
			TokensParticle.class,
			ParticleDefinition.<TokensParticle>builder()
				.allowTransitionsFromOutsideScrypts()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TokensParticle::getTokDefRef)
				.build()
		);

	}

	private void defineTokenCreation(SysCalls os) {
		os.createTransitionFromRRICombined(
			TokenDefinitionParticle.class,
			TokensParticle.class,
			t -> !t.isMutable(),
			TokensConstraintScrypt::checkCreateTransferrable
		);
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.executeRoutine(calls -> {
			calls.createTransition(
				new TransitionToken<>(
					TokenDefinitionParticle.class,
					TokensParticle.class,
					TypeToken.of(ReadOnlyData.class)
				),
				new TransitionProcedure<>() {
					@Override
					public Result precondition(
						TokenDefinitionParticle inputParticle,
						TokensParticle outputParticle,
						ReadOnlyData inputUsed
					) {
						if (!inputParticle.isMutable()) {
							return Result.error("Can only mint mutable tokens.");
						}

						if (!outputParticle.isBurnable()) {
							return Result.error("Must be able to burn mutable token.");
						}

						if (!inputParticle.getRRI().equals(outputParticle.getTokDefRef())) {
							return Result.error("Minted token must be equivalent to token def.");
						}

						return Result.success();
					}

					@Override
					public InputOutputReducer<TokenDefinitionParticle, TokensParticle, ReadOnlyData>
						inputOutputReducer() {
						return (inputParticle, outputParticle, index, outputUsed)
							-> ReducerResult.complete(new MintToken(
								inputParticle.getRRI(), outputParticle.getAddress(), outputParticle.getAmount()
						));
					}

					@Override
					public SignatureValidator<TokenDefinitionParticle> inputSignatureRequired() {
						return i -> Optional.of(i.getRRI().getAddress());
					}
				}
			);
		});

		// Transfers
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TokensParticle.class,
			TokensParticle.class,
			TokensParticle::getAmount,
			TokensParticle::getAmount,
			checkEquals(
				TokensParticle::isBurnable,
				TokensParticle::isBurnable,
				"Permissions not equal."
			),
			i -> Optional.of(i.getAddress()),
			(i, o) -> new TransferToken(i.getTokDefRef(), o.getAddress(), o.getAmount()) // FIXME: This isn't 100% correct
		));


		// Burns
		os.executeRoutine(calls -> {
			calls.createTransition(
				new TransitionToken<>(
					TokensParticle.class,
					VoidParticle.class,
					TypeToken.of(CreateFungibleTransitionRoutine.UsedAmount.class)
				),
				new TransitionProcedure<>() {
					@Override
					public Result precondition(
						TokensParticle inputParticle,
						VoidParticle outputParticle,
						CreateFungibleTransitionRoutine.UsedAmount inputUsed
					) {
						if (!inputUsed.isInput()) {
							return Result.error("Broken state.");
						}

						if (!inputParticle.isBurnable()) {
							return Result.error("Cannot burn token.");
						}

						return Result.success();
					}

					@Override
					public InputOutputReducer<TokensParticle, VoidParticle, UsedAmount> inputOutputReducer() {
						return (inputParticle, outputParticle, index, state) -> {
							var amt = inputParticle.getAmount().subtract(state.getUsedAmount());
							return ReducerResult.complete(new BurnToken(inputParticle.getTokDefRef(), amt));
						};
					}

					@Override
					public SignatureValidator<TokensParticle> inputSignatureRequired() {
						return i -> Optional.of(i.getAddress());
					}
				}
			);
		});
	}

	@VisibleForTesting
	static Result checkCreateTransferrable(TokenDefinitionParticle tokDef, TokensParticle transferrable) {
		if (!Objects.equals(tokDef.getSupply().orElseThrow(), transferrable.getAmount())) {
			return Result.error("Supply and amount are not equal.");
		}

		if (transferrable.isBurnable()) {
			return Result.error("Tokens must be non-mutable.");
		}

		return Result.success();
	}

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0);
	}

}
