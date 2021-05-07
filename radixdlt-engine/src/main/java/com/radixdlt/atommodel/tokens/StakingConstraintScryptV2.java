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
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public final class StakingConstraintScryptV2 implements ConstraintScrypt {
	private final int epochsLocked;

	public StakingConstraintScryptV2(int epochsLocked) {
		if (epochsLocked < 0) {
			throw new IllegalArgumentException("epochsLocked must be >= 0");
		}

		this.epochsLocked = epochsLocked;
	}

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			StakedTokensParticle.class,
			ParticleDefinition.<StakedTokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(p -> REAddr.ofNativeToken())
				.build()
		);

		os.registerParticle(
			ExitingStake.class,
			ParticleDefinition.<ExitingStake>builder()
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
			(i, o, r) -> {
				if (!Objects.equals(i.getHoldingAddr(), o.getOwner())) {
					return Result.error("Owner must remain the same");
				}
				return Result.success();
			},
			(i, o, r, pubKey) -> pubKey.map(i.getSubstate().getHoldingAddr()::allowToWithdrawFrom).orElse(false),
			(i, o, r) -> new StakeTokens(o.getOwner(), o.getDelegateKey(), o.getAmount()) // FIXME: this isn't 100% correct
		));

		// For change
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			StakedTokensParticle.class,
			(i, o, r) -> {
				if (!Objects.equals(i.getOwner(), o.getOwner())) {
					return Result.error("Owners must be same");
				}
				if (!Objects.equals(i.getDelegateKey(), o.getDelegateKey())) {
					return Result.error("Delegate must be same");
				}

				return Result.success();
			},
			(i, o, r, pubKey) -> pubKey.map(i.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			(i, o, r) -> Unknown.create()
		));

		// Exiting
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			StakedTokensParticle.class,
			ExitingStake.class,
			(i, o, r) -> {
				if (!Objects.equals(i.getOwner(), o.getOwner())) {
					return Result.error("Must unstake to self");
				}

				var system = (SystemParticle) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
				if (system.getEpoch() != o.getEpochExit()) {
					return Result.error("Incorrect epoch: " + o.getEpochExit() + " current is: " + system.getEpoch());
				}

				return Result.success();
			},
			(i, o, r, pubKey) -> pubKey.map(i.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			(i, o, r) -> new UnstakeTokens(i.getOwner(), i.getDelegateKey(), o.getAmount()) // FIXME: this isn't 100% correct
		));

		// Exit
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			ExitingStake.class,
			TokensParticle.class,
			(i, o, r) -> {
				var system = (SystemParticle) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
				var epochUnlocked = i.getEpochExit() + epochsLocked;
				if (system.getEpoch() < epochUnlocked) {
					return Result.error("Cannot unlock tokens until epoch: " + epochUnlocked + " current: " + system.getEpoch());
				}

				return Result.success();
			},
			(i, o, r, pubKey) -> pubKey.map(i.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			(i, o, r) -> Unknown.create()
		));
	}
}
