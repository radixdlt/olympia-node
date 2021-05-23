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

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.InvalidResourceException;
import com.radixdlt.constraintmachine.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Objects;
import java.util.Optional;

public class StakingConstraintScryptV3 implements ConstraintScrypt {
	public static final int EPOCHS_LOCKED = 2; // Must go through one full epoch before being unlocked

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			PreparedStake.class,
			ParticleDefinition.<PreparedStake>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		defineStaking(os);
	}

	public static class StakeHoldingBucket implements ReducerState {
		private final UInt384 amount;
		private final REAddr accountAddr;
		private final ECPublicKey delegate;

		public StakeHoldingBucket(
			ECPublicKey delegate,
			REAddr accountAddr,
			UInt384 amount
		) {
			this.delegate = delegate;
			this.accountAddr = accountAddr;
			this.amount = amount;
		}

		public StakeHoldingBucket deposit(REAddr resourceAddr, UInt256 amountToAdd) throws ProcedureException {
			if (!resourceAddr.isNativeToken()) {
				throw new InvalidResourceException(resourceAddr, REAddr.ofNativeToken());
			}

			return new StakeHoldingBucket(delegate, accountAddr, UInt384.from(amountToAdd).add(amount));
		}

		public StakeHoldingBucket withdraw(REAddr resourceAddr, UInt256 amountToWithdraw) throws ProcedureException {
			if (!resourceAddr.isNativeToken()) {
				throw new InvalidResourceException(resourceAddr, REAddr.ofNativeToken());
			}

			var withdraw384 = UInt384.from(amountToWithdraw);
			if (amount.compareTo(withdraw384) < 0) {
				throw new NotEnoughResourcesException(amountToWithdraw, amount.getLow());
			}

			return new StakeHoldingBucket(delegate, accountAddr, amount.subtract(withdraw384));
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(TokensConstraintScryptV2.TokenHoldingBucket.class);
		}
	}

	private void defineStaking(SysCalls os) {
		// Stake
		os.createUpProcedure(new UpProcedure<>(
			TokensConstraintScryptV2.TokenHoldingBucket.class, PreparedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				var nextState = s.withdraw(REAddr.ofNativeToken(), u.getAmount());

				if (s.from() != null) {
					var actionGuess = new StakeTokens(s.from(), u.getDelegateKey(), u.getAmount());
					return ReducerResult.incomplete(nextState, actionGuess);
				}

				return ReducerResult.incomplete(nextState, Unknown.create());
			}
		));

		// Unstake
		os.createDownProcedure(new DownProcedure<>(
			PreparedStake.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				try {
					d.getSubstate().getOwner().verifyWithdrawAuthorization(k);
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			},
			(d, s, r) -> {
				var substate = d.getSubstate();
				var nextState = new StakeHoldingBucket(substate.getDelegateKey(), substate.getOwner(), UInt384.from(substate.getAmount()));
				return ReducerResult.incomplete(nextState);
			}
		));
		// Additional Unstake
		os.createDownProcedure(new DownProcedure<>(
			PreparedStake.class, StakeHoldingBucket.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				try {
					d.getSubstate().getOwner().verifyWithdrawAuthorization(k);
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			},
			(d, s, r) -> {
				var stake = d.getSubstate();
				if (!s.delegate.equals(stake.getDelegateKey())) {
					throw new ProcedureException("Delegate keys not equivalent.");
				}
				if (!s.accountAddr.equals(stake.getOwner())) {
					throw new ProcedureException("Account addresses not equivalent.");
				}
				return ReducerResult.incomplete(s.deposit(stake.getResourceAddr(), stake.getAmount()));
			}
		));
		// Change
		os.createUpProcedure(new UpProcedure<>(
			StakeHoldingBucket.class, PreparedStake.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> { },
			(s, u, r) -> {
				if (!u.getDelegateKey().equals(s.delegate)) {
					throw new ProcedureException("Delegate key does not match.");
				}

				if (!u.getOwner().equals(s.accountAddr)) {
					throw new ProcedureException("Owners don't match.");
				}

				var nextState = s.withdraw(u.getResourceAddr(), u.getAmount());
				return ReducerResult.incomplete(nextState);
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			StakeHoldingBucket.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!u.getResourceAddr().isNativeToken()) {
					throw new ProcedureException("Can only destake to the native token.");
				}

				if (!Objects.equals(s.accountAddr, u.getHoldingAddr())) {
					throw new ProcedureException("Must unstake to self");
				}

				if (u.getEpochUnlocked().isEmpty()) {
					throw new ProcedureException("Exiting from stake must be locked.");
				}

				var system = (SystemParticle) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
				if (system.getEpoch() + EPOCHS_LOCKED != u.getEpochUnlocked().get()) {
					throw new ProcedureException("Incorrect epoch unlock: " + u.getEpochUnlocked().get()
						+ " should be: " + (system.getEpoch() + EPOCHS_LOCKED));
				}

				var nextState = s.withdraw(u.getResourceAddr(), u.getAmount());
				var actionGuess = new UnstakeTokens(s.accountAddr, s.delegate, u.getAmount());
				return ReducerResult.incomplete(nextState, actionGuess);
			}
		));

		// Deallocate Stake Holding Bucket
		os.createEndProcedure(new EndProcedure<>(
			StakeHoldingBucket.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> { },
			(s, r) -> {
				if (!s.amount.isZero()) {
					throw new ProcedureException("Stake cannot be burnt.");
				}

				return Optional.empty();
			}
		));
	}
}
