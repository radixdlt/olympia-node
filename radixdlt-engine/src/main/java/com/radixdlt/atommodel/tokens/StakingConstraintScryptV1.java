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

import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atom.actions.DeprecatedUnstakeTokens;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;
import java.util.function.Function;

public final class StakingConstraintScryptV1 implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			StakedTokensParticle.class,
			ParticleDefinition.<StakedTokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(p -> REAddr.ofNativeToken())
				.build()
		);

		defineStaking(os);
	}


	private void defineStaking(SysCalls os) {
		// Staking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TokensParticle.class,
			StakedTokensParticle.class,
			checkEquals(
				TokensParticle::getHoldingAddr,
				StakedTokensParticle::getOwner,
				"Can only stake with self as owner"
			),
			(i, o, index, pubKey) -> pubKey.map(i.getSubstate().getHoldingAddr()::allowToWithdrawFrom).orElse(false),
			(i, o, index) -> new StakeTokens(o.getOwner(), o.getDelegateKey(), o.getAmount()) // FIXME: this isn't 100% correct
		));

		// Unstaking
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			TokensParticle.class,
			(i, o, r) -> {
				if (!Objects.equals(i.getOwner(), o.getHoldingAddr())) {
					return Result.error("Must unstake to self");
				}

				var epochUnlocked = o.getEpochUnlocked();
				if (epochUnlocked.isPresent()) {
					return Result.error("Cannot be locked for BetanetV1.");
				}

				return Result.success();
			},
			(i, o, index, pubKey) -> pubKey.map(i.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			// FIXME: this isn't 100% correct
			(i, o, index) -> new DeprecatedUnstakeTokens(i.getOwner(), i.getDelegateKey(), o.getAmount())
		));

		// Stake movement
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			StakedTokensParticle.class,
			checkEquals(
				StakedTokensParticle::getOwner,
				StakedTokensParticle::getOwner,
				"Can't send staked tokens to another address."
			),
			(i, o, index, pubKey) -> pubKey.map(i.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			(i, o, index) -> Unknown.create()
		));
	}

	private static <L extends Fungible, R extends Fungible, R0, R1> CreateFungibleTransitionRoutine.Verifier<L, R> checkEquals(
		Function<L, R0> leftMapper0, Function<R, R0> rightMapper0, String errorMessage0
	) {
		return (l, r, verifier) -> Result.of(Objects.equals(leftMapper0.apply(l), rightMapper0.apply(r)), errorMessage0);
	}
}
