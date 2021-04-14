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

import com.radixdlt.atom.actions.StakeNativeToken;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atom.actions.UnstakeNativeToken;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RriId;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.identifiers.RRI;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class StakingConstraintScrypt implements ConstraintScrypt {
	private final RRI stakingToken;

	public StakingConstraintScrypt(RRI stakingToken) {
		this.stakingToken = stakingToken;
	}

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			StakedTokensParticle.class,
			ParticleDefinition.<StakedTokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(p -> RriId.fromRri(stakingToken))
				.build()
		);

		defineStaking(os);
	}


	private void defineStaking(SysCalls os) {
		// Staking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TokensParticle.class,
			StakedTokensParticle.class,
			TokensParticle::getAmount,
			StakedTokensParticle::getAmount,
			(i, o) -> Result.success(),
			i -> Optional.of(i.getAddress()),
			(i, o, index) -> new StakeNativeToken(stakingToken, o.getDelegateAddress(), o.getAmount()) // FIXME: this isn't 100% correct
		));

		// Unstaking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			TokensParticle.class,
			StakedTokensParticle::getAmount,
			TokensParticle::getAmount,
			(i, o) -> Result.success(),
			i -> Optional.of(i.getAddress()),
			(i, o, index) -> new UnstakeNativeToken(stakingToken, i.getDelegateAddress(), o.getAmount()) // FIXME: this isn't 100% correct
		));

		// Stake movement
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			StakedTokensParticle.class,
			StakedTokensParticle::getAmount,
			StakedTokensParticle::getAmount,
			checkEquals(
				StakedTokensParticle::getAddress,
				StakedTokensParticle::getAddress,
				"Can't send staked tokens to another address."
			),
			i -> Optional.of(i.getAddress()),
			(i, o, index) -> Unknown.create()
		));
	}

	private static <L, R, R0, R1> BiFunction<L, R, Result> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0
	) {
		return (l, r) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0);
	}
}
