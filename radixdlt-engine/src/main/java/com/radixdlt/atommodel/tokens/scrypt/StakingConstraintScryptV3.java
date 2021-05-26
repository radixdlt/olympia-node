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

import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.system.state.Stake;
import com.radixdlt.atommodel.system.state.StakeShares;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.ReadableAddrs;
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

	public static class StakeSharesHoldingBucket implements ReducerState {
		private final UInt384 stakeAmount;
		private final UInt384 shareAmount;
		private final REAddr accountAddr;
		private final ECPublicKey delegate;

		public StakeSharesHoldingBucket(StakeShares stakeShares) {
			this(stakeShares.getDelegateKey(), stakeShares.getOwner(), UInt384.from(stakeShares.getAmount()), UInt384.ZERO);
		}

		public StakeSharesHoldingBucket(
			ECPublicKey delegate,
			REAddr accountAddr,
			UInt384 amount,
			UInt384 stakeAmount
		) {
			this.delegate = delegate;
			this.accountAddr = accountAddr;
			this.shareAmount = amount;
			this.stakeAmount = stakeAmount;
		}

		public StakeSharesHoldingBucket withdrawShares(StakeShares stakeShares) throws ProcedureException {
			if (!delegate.equals(stakeShares.getDelegateKey())) {
				throw new ProcedureException("Shares must be from same delegate");
			}
			if (!stakeShares.getOwner().equals(accountAddr)) {
				throw new ProcedureException("Shares must be for same account");
			}
			var withdraw384 = UInt384.from(stakeShares.getAmount());
			if (shareAmount.compareTo(withdraw384) < 0) {
				throw new NotEnoughResourcesException(stakeShares.getAmount(), shareAmount.getLow());
			}

			return new StakeSharesHoldingBucket(delegate, accountAddr, shareAmount.subtract(withdraw384), stakeAmount);
		}

		public StakeSharesHoldingBucket depositShares(StakeShares stakeShares) throws ProcedureException {
			if (!delegate.equals(stakeShares.getDelegateKey())) {
				throw new ProcedureException("Shares must be from same delegate");
			}
			if (!stakeShares.getOwner().equals(accountAddr)) {
				throw new ProcedureException("Shares must be for same account");
			}
			return new StakeSharesHoldingBucket(delegate, accountAddr, UInt384.from(stakeShares.getAmount()).add(shareAmount), stakeAmount);
		}

		public StakeSharesHoldingBucket depositStake(Stake stake) throws ProcedureException {
			if (!delegate.equals(stake.getValidatorKey())) {
				throw new ProcedureException("Stake must be from delegate " + delegate);
			}
			return new StakeSharesHoldingBucket(delegate, accountAddr, shareAmount, stakeAmount.add(stake.getAmount()));
		}

		public StakeSharesHoldingBucket withdrawStake(Stake stake) throws ProcedureException {
			if (!delegate.equals(stake.getValidatorKey())) {
				throw new ProcedureException("Stake must be from delegate " + delegate);
			}
			var withdraw384 = UInt384.from(stake.getAmount());
			if (stakeAmount.compareTo(withdraw384) < 0) {
				throw new NotEnoughResourcesException(stake.getAmount(), stakeAmount.getLow());
			}

			return new StakeSharesHoldingBucket(delegate, accountAddr, shareAmount, stakeAmount.subtract(withdraw384));
		}

		public StakeSharesHoldingBucket unstake(TokensParticle u, ReadableAddrs r) throws ProcedureException {
			if (!u.getResourceAddr().isNativeToken()) {
				throw new ProcedureException("Can only destake to the native token.");
			}

			if (!Objects.equals(accountAddr, u.getHoldingAddr())) {
				throw new ProcedureException("Must unstake to self");
			}

			if (u.getEpochUnlocked().isEmpty()) {
				throw new ProcedureException("Exiting from stake must be locked.");
			}

			var unstakeAmount = UInt384.from(u.getAmount());
			if (stakeAmount.compareTo(unstakeAmount) < 0) {
				throw new NotEnoughResourcesException(u.getAmount(), stakeAmount.getLow());
			}
			if (stakeAmount.compareTo(shareAmount) < 0) {
				throw new NotEnoughResourcesException(u.getAmount(), shareAmount.getLow());
			}

			var systemState = (HasEpochData) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
			if (systemState.getEpoch() + EPOCHS_LOCKED != u.getEpochUnlocked().get()) {
				throw new ProcedureException("Incorrect epoch unlock: " + u.getEpochUnlocked().get()
					+ " should be: " + (systemState.getEpoch() + EPOCHS_LOCKED));
			}

			return new StakeSharesHoldingBucket(
				delegate,
				accountAddr,
				shareAmount.subtract(unstakeAmount),
				stakeAmount.subtract(unstakeAmount)
			);


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
			StakeShares.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				try {
					d.getSubstate().getOwner().verifyWithdrawAuthorization(k);
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new StakeSharesHoldingBucket(d.getSubstate()))
		));
		// Additional Unstake
		os.createDownProcedure(new DownProcedure<>(
			StakeShares.class, StakeSharesHoldingBucket.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				try {
					d.getSubstate().getOwner().verifyWithdrawAuthorization(k);
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			},
			(d, s, r) -> ReducerResult.incomplete(s.depositShares(d.getSubstate()))
		));
		// Change
		os.createUpProcedure(new UpProcedure<>(
			StakeSharesHoldingBucket.class, StakeShares.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.withdrawShares(u))
		));

		// Unstake
		os.createDownProcedure(new DownProcedure<>(
			Stake.class, StakeSharesHoldingBucket.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> { },
			(d, s, r) -> ReducerResult.incomplete(s.depositStake(d.getSubstate()))
		));
		// Unstake change
		os.createUpProcedure(new UpProcedure<>(
			StakeSharesHoldingBucket.class, Stake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.withdrawStake(u))
		));

		// Unstake to locked tokens
		os.createUpProcedure(new UpProcedure<>(
			StakeSharesHoldingBucket.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				var nextState = s.unstake(u, r);
				var actionGuess = new UnstakeTokens(s.accountAddr, s.delegate, u.getAmount());
				return ReducerResult.incomplete(nextState, actionGuess);
			}
		));

		// Deallocate Stake Holding Bucket
		os.createEndProcedure(new EndProcedure<>(
			StakeSharesHoldingBucket.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> { },
			(s, r) -> {
				if (!s.shareAmount.isZero()) {
					throw new ProcedureException("Shares cannot be burnt.");
				}
				if (!s.stakeAmount.isZero()) {
					throw new ProcedureException("Stake cannot be burnt.");
				}

				return Optional.empty();
			}
		));
	}
}
