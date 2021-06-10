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

package com.radixdlt.atommodel.tokens.scrypt;

import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;

import java.util.Objects;

public final class StakingConstraintScryptV1 implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		os.particle(
			PreparedStake.class,
			ParticleDefinition.<PreparedStake>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		defineStaking(os);
	}


	private void defineStaking(Loader os) {
		// Stake
		os.procedure(new UpProcedure<>(
			VoidReducerState.class, PreparedStake.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var state = new StakingConstraintScryptV2.UnaccountedStake(
					u,
					UInt384.from(u.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));
		os.procedure(new DownProcedure<>(
			TokensInAccount.class, StakingConstraintScryptV2.UnaccountedStake.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				if (!d.getSubstate().getResourceAddr().isNativeToken()) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					return ReducerResult.complete();
				}

				return ReducerResult.incomplete(nextRemainder.get());
			}
		));


		// Unstake
		os.procedure(new DownProcedure<>(
			PreparedStake.class, TokensConstraintScryptV1.UnaccountedTokens.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					try {
						d.getSubstate().getOwner().verifyWithdrawAuthorization(c.key());
					} catch (REAddr.BucketWithdrawAuthorizationException e) {
						throw new AuthorizationException(e.getMessage());
					}
				}),
			(d, s, r) -> {
				if (!s.resourceInBucket().isNativeToken()) {
					throw new ProcedureException("Can only destake to the native token.");
				}

				if (!Objects.equals(d.getSubstate().getOwner(), s.resourceInBucket().holdingAddress())) {
					throw new ProcedureException("Must unstake to self");
				}

				var epochUnlocked = s.resourceInBucket().epochUnlocked();
				if (epochUnlocked.isPresent()) {
					throw new ProcedureException("Cannot be locked for betanetV1");
				}

				var nextRemainder = s.subtract(UInt384.from(d.getSubstate().getAmount()));
				if (nextRemainder.isEmpty()) {
					return ReducerResult.complete();
				}

				if (nextRemainder.get() instanceof TokensConstraintScryptV1.RemainderTokens) {
					TokensConstraintScryptV1.RemainderTokens remainderTokens = (TokensConstraintScryptV1.RemainderTokens) nextRemainder.get();
					var stakeRemainder = new StakingConstraintScryptV2.RemainderStake(
						remainderTokens.amount().getLow(),
						d.getSubstate().getOwner(),
						d.getSubstate().getDelegateKey()
					);
					return ReducerResult.incomplete(stakeRemainder);
				} else {
					return ReducerResult.incomplete(nextRemainder.get());
				}
			}
		));

		// For change
		os.procedure(new UpProcedure<>(
			StakingConstraintScryptV2.RemainderStake.class, PreparedStake.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getAmount().equals(s.amount())) {
					throw new ProcedureException("Remainder must be filled exactly.");
				}

				if (!u.getDelegateKey().equals(s.delegate())) {
					throw new ProcedureException("Delegate key does not match.");
				}

				if (!u.getOwner().equals(s.owner())) {
					throw new ProcedureException("Owners don't match.");
				}

				return ReducerResult.complete();
			}
		));
	}
}
