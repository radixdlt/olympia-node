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
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.store.ImmutableIndex;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Scrypt which defines how tokens are managed.
 */
public class TokensConstraintScrypt implements ConstraintScrypt {
	private final Set<String> systemNames;

	public TokensConstraintScrypt(Set<String> systemNames) {
		this.systemNames = Objects.requireNonNull(systemNames);
	}

	public TokensConstraintScrypt() {
		this(Set.of());
	}

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
				.staticValidation(p -> TokenDefinitionUtils.staticCheck(p, systemNames))
				.rriMapper(TokenDefinitionParticle::getRriId)
				.build()
		);

		os.registerParticle(
			TokensParticle.class,
			ParticleDefinition.<TokensParticle>builder()
				.allowTransitionsFromOutsideScrypts()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TokensParticle::getRriId)
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
					VoidParticle.class,
					TokensParticle.class,
					TypeToken.of(VoidReducerState.class)
				),
				new TransitionProcedure<>() {
					@Override
					public Result precondition(
						VoidParticle inputParticle,
						TokensParticle outputParticle,
						VoidReducerState inputUsed,
						ImmutableIndex immutableIndex
					) {
						var p = immutableIndex.loadRriId(null, outputParticle.getRriId());
						if (p.isEmpty()) {
							return Result.error("Token does not exist.");
						}

						var particle = p.get();
						if (!(particle instanceof TokenDefinitionParticle)) {
							return Result.error("Rri is not a token");
						}

						var tokenDef = (TokenDefinitionParticle) particle;

						if (!tokenDef.isMutable()) {
							return Result.error("Can only mint mutable tokens.");
						}

						return Result.success();
					}

					@Override
					public InputOutputReducer<VoidParticle, TokensParticle, VoidReducerState>
						inputOutputReducer() {
						return (i, o, index, outputUsed) -> {
							var tokenDef = (TokenDefinitionParticle) index.loadRriId(null, o.getRriId()).orElseThrow();
							return ReducerResult.complete(
								new MintToken(tokenDef.getRri(), o.getAddress(), o.getAmount())
							);
						};
					}

					@Override
					public SignatureValidator<VoidParticle, TokensParticle> signatureRequired() {
						return (i, o, index) -> {
							var tokenDef = (TokenDefinitionParticle) index.loadRriId(null, o.getRriId()).orElseThrow();
							return tokenDef.getRri().getAddress();
						};
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
			(i, o) -> Result.success(),
			(i, o, index) -> Optional.of(i.getAddress()),
			(i, o, index) -> {
				var p = (TokenDefinitionParticle) index.loadRriId(null, i.getRriId()).orElseThrow();
				return new TransferToken(p.getRri(), o.getAddress(), o.getAmount()); // FIXME: This isn't 100% correct
			}
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
						CreateFungibleTransitionRoutine.UsedAmount inputUsed,
						ImmutableIndex immutableIndex
					) {
						if (!inputUsed.isInput()) {
							return Result.error("Broken state.");
						}

						var p = immutableIndex.loadRriId(null, inputParticle.getRriId());
						if ((p.isEmpty() || !(p.get() instanceof TokenDefinitionParticle))) {
							return Result.error("Bad rriId");
						}
						var token = (TokenDefinitionParticle) p.get();
						if (!token.isMutable())	{
							return Result.error("Cannot burn Fixed supply token.");
						}

						return Result.success();
					}

					@Override
					public InputOutputReducer<TokensParticle, VoidParticle, UsedAmount> inputOutputReducer() {
						return (i, o, index, state) -> {
							var amt = i.getAmount().subtract(state.getUsedAmount());
							var p = (TokenDefinitionParticle) index.loadRriId(null, i.getRriId()).orElseThrow();
							return ReducerResult.complete(new BurnToken(p.getRri(), amt));
						};
					}

					@Override
					public SignatureValidator<TokensParticle, VoidParticle> signatureRequired() {
						return (i, o, index) -> Optional.of(i.getAddress());
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

		return Result.success();
	}
}
