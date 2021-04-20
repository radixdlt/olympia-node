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

package com.radixdlt.atommodel.routines;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atomos.ConstraintRoutine;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RoutineCalls;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.VoidParticle;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.store.ImmutableIndex;

public final class DeallocateTokensRoutine implements ConstraintRoutine {
	@Override
	public void main(RoutineCalls calls) {
		calls.createTransition(
			new TransitionToken<>(
				TokensParticle.class,
				VoidParticle.class,
				TypeToken.of(VoidReducerState.class)
			),
			new TransitionProcedure<>() {
				@Override
				public Result precondition(
					TokensParticle inputParticle,
					VoidParticle outputParticle,
					VoidReducerState voidReducerState,
					ImmutableIndex immutableIndex
				) {
					var p = immutableIndex.loadRri(null, inputParticle.getRri());
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
				public InputOutputReducer<TokensParticle, VoidParticle, VoidReducerState> inputOutputReducer() {
					return (i, o, index, state) -> {
						return ReducerResult.complete(new BurnToken(i.getRri(), i.getAmount()));
					};
				}

				@Override
				public SignatureValidator<TokensParticle, VoidParticle> signatureValidator() {
					return (i, o, index, pubKey) -> pubKey.map(i.getAddress()::ownedBy).orElse(false);
				}
			}
		);

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

					var p = immutableIndex.loadRri(null, inputParticle.getRri());
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
				public InputOutputReducer<TokensParticle, VoidParticle, CreateFungibleTransitionRoutine.UsedAmount>
					inputOutputReducer() {
					return (i, o, index, state) -> {
						var amt = i.getAmount().subtract(state.getUsedAmount());
						var p = (TokenDefinitionParticle) index.loadRri(null, i.getRri()).orElseThrow();
						return ReducerResult.complete(new BurnToken(p.getRri(), amt));
					};
				}

				@Override
				public SignatureValidator<TokensParticle, VoidParticle> signatureValidator() {
					return (i, o, index, pubKey) -> pubKey.map(i.getAddress()::ownedBy).orElse(false);
				}
			}
		);
	}
}
