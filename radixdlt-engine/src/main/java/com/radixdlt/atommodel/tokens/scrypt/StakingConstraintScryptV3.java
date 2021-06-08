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
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
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
import com.radixdlt.utils.UInt384;

import java.util.Objects;
import java.util.Optional;

public class StakingConstraintScryptV3 implements ConstraintScrypt {

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			PreparedStake.class,
			ParticleDefinition.<PreparedStake>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		os.registerParticle(
			PreparedUnstakeOwnership.class,
			ParticleDefinition.<PreparedUnstakeOwnership>builder()
				.staticValidation(p -> {
					if (p.getAmount().isZero()) {
						return Result.error("amount must not be zero");
					}
					return Result.success();
				})
				.build()
		);


		defineStaking(os);
	}

	public static class StakeSharesHoldingBucket implements ReducerState {
		private final UInt384 shareAmount;
		private final REAddr accountAddr;
		private final ECPublicKey delegate;

		public StakeSharesHoldingBucket(StakeOwnership stakeOwnership) {
			this(stakeOwnership.getDelegateKey(), stakeOwnership.getOwner(), UInt384.from(stakeOwnership.getAmount()));
		}

		public StakeSharesHoldingBucket(
			ECPublicKey delegate,
			REAddr accountAddr,
			UInt384 amount
		) {
			this.delegate = delegate;
			this.accountAddr = accountAddr;
			this.shareAmount = amount;
		}

		public StakeSharesHoldingBucket withdrawShares(StakeOwnership stakeOwnership) throws ProcedureException {
			if (!delegate.equals(stakeOwnership.getDelegateKey())) {
				throw new ProcedureException("Shares must be from same delegate");
			}
			if (!stakeOwnership.getOwner().equals(accountAddr)) {
				throw new ProcedureException("Shares must be for same account");
			}
			var withdraw384 = UInt384.from(stakeOwnership.getAmount());
			if (shareAmount.compareTo(withdraw384) < 0) {
				throw new NotEnoughResourcesException(stakeOwnership.getAmount(), shareAmount.getLow());
			}

			return new StakeSharesHoldingBucket(delegate, accountAddr, shareAmount.subtract(withdraw384));
		}

		public StakeSharesHoldingBucket depositShares(StakeOwnership stakeOwnership) throws ProcedureException {
			if (!delegate.equals(stakeOwnership.getDelegateKey())) {
				throw new ProcedureException("Shares must be from same delegate");
			}
			if (!stakeOwnership.getOwner().equals(accountAddr)) {
				throw new ProcedureException("Shares must be for same account");
			}
			return new StakeSharesHoldingBucket(delegate, accountAddr, UInt384.from(stakeOwnership.getAmount()).add(shareAmount));
		}

		public StakeSharesHoldingBucket unstake(PreparedUnstakeOwnership u) throws ProcedureException {
			if (!Objects.equals(accountAddr, u.getOwner())) {
				throw new ProcedureException("Must unstake to self");
			}

			var unstakeAmount = UInt384.from(u.getAmount());
			if (shareAmount.compareTo(unstakeAmount) < 0) {
				throw new NotEnoughResourcesException(u.getAmount(), shareAmount.getLow());
			}

			return new StakeSharesHoldingBucket(
				delegate,
				accountAddr,
				shareAmount.subtract(unstakeAmount)
			);
		}

		public void destroy() throws ProcedureException {
			if (!shareAmount.isZero()) {
				throw new ProcedureException("Shares cannot be burnt.");
			}
		}
	}

	private void defineStaking(SysCalls os) {
		// Stake
		os.createUpProcedure(new UpProcedure<>(
			TokensConstraintScryptV2.TokenHoldingBucket.class, PreparedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (u.getAmount().compareTo(ValidatorStake.MINIMUM_STAKE) < 0) {
					throw new ProcedureException(
						"Minimum amount to stake must be >= " + ValidatorStake.MINIMUM_STAKE
							+ " but trying to stake " + u.getAmount()
					);
				}

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
			StakeOwnership.class, VoidReducerState.class,
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
			StakeOwnership.class, StakeSharesHoldingBucket.class,
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
			StakeSharesHoldingBucket.class, StakeOwnership.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.withdrawShares(u))
		));
		os.createUpProcedure(new UpProcedure<>(
			StakeSharesHoldingBucket.class, PreparedUnstakeOwnership.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				var actionGuess = new UnstakeOwnership(s.accountAddr, u.getDelegateKey(), u.getAmount());
				return ReducerResult.incomplete(s.unstake(u), actionGuess);
			}));

		// Deallocate Stake Holding Bucket
		os.createEndProcedure(new EndProcedure<>(
			StakeSharesHoldingBucket.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> { },
			(s, r) -> {
				s.destroy();
				return Optional.empty();
			}
		));
	}
}
