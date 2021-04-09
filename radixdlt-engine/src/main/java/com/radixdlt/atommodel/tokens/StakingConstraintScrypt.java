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

package com.radixdlt.atommodel.tokens;

import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class StakingConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			StakedTokensParticle.class,
			ParticleDefinition.<StakedTokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(StakedTokensParticle::getTokDefRef)
				.build()
		);

		defineStaking(os);
	}


	private void defineStaking(SysCalls os) {
		// Staking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TransferrableTokensParticle.class,
			StakedTokensParticle.class,
			TransferrableTokensParticle::getAmount,
			StakedTokensParticle::getAmount,
			checkEquals(
				TransferrableTokensParticle::isMutable,
				StakedTokensParticle::isMutable,
				"Mutability not equal."
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
				StakedTokensParticle::isMutable,
				TransferrableTokensParticle::isMutable,
				"Mutability not equal."
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
				StakedTokensParticle::isMutable,
				StakedTokensParticle::isMutable,
				"Mutability not equal.",
				StakedTokensParticle::getAddress,
				StakedTokensParticle::getAddress,
				"Can't send staked tokens to another address."
			),
			(in, meta) -> checkSignedBy(meta, in.getAddress())
		));
	}

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0);
	}

	static WitnessValidator.WitnessValidatorResult checkSignedBy(WitnessData meta, RadixAddress address) {
		return meta.isSignedBy(address.getPublicKey())
			? WitnessValidator.WitnessValidatorResult.success()
			: WitnessValidator.WitnessValidatorResult.error(String.format("Not signed by: %s", address.getPublicKey()));
	}

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0,
		Function<L, R1> leftMapper1, Function<R, R1> rightMapper1, String errorMessage1
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0)
			.mapSuccess(() -> Result.of(Objects.equals(leftMapper1.apply(l), rightMapper1.apply(r)), errorMessage1));
	}
}
