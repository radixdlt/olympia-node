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

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.InvalidDelegationException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.MinimumStakeException;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.function.Predicate;

public final class StakingConstraintScryptV4 implements ConstraintScrypt {
	private final UInt256 minimumStake;

	public StakingConstraintScryptV4(UInt256 minimumStake) {
		this.minimumStake = minimumStake;
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				PreparedStake.class,
				SubstateTypeId.PREPARED_STAKE.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeAccountREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new PreparedStake(amount, owner, delegate);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		os.substate(
			new SubstateDefinition<>(
				PreparedUnstakeOwnership.class,
				SubstateTypeId.PREPARED_UNSTAKE.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeAccountREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new PreparedUnstakeOwnership(delegate, owner, amount);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		defineStaking(os);
	}

	private final class OwnerStakePrepare implements ReducerState {
		private final TokenHoldingBucket tokenHoldingBucket;
		private final AllowDelegationFlag allowDelegationFlag;

		OwnerStakePrepare(TokenHoldingBucket tokenHoldingBucket, AllowDelegationFlag allowDelegationFlag) {
			this.tokenHoldingBucket = tokenHoldingBucket;
			this.allowDelegationFlag = allowDelegationFlag;
		}

		ReducerState readOwner(ValidatorOwnerCopy ownerCopy) throws ProcedureException {
			if (!allowDelegationFlag.getValidatorKey().equals(ownerCopy.getValidatorKey())) {
				throw new ProcedureException("Not matching validator keys");
			}
			var owner = ownerCopy.getOwner().orElse(REAddr.ofPubKeyAccount(ownerCopy.getValidatorKey()));
			return new StakePrepare(
				tokenHoldingBucket,
				allowDelegationFlag.getValidatorKey(),
				owner::equals
			);
		}
	}

	private final class StakePrepare implements ReducerState {
		private final TokenHoldingBucket tokenHoldingBucket;
		private final ECPublicKey validatorKey;
		private final Predicate<REAddr> delegateAllowed;

		StakePrepare(TokenHoldingBucket tokenHoldingBucket, ECPublicKey validatorKey, Predicate<REAddr> delegateAllowed) {
			this.tokenHoldingBucket = tokenHoldingBucket;
			this.validatorKey = validatorKey;
			this.delegateAllowed = delegateAllowed;
		}

		ReducerState withdrawTo(PreparedStake preparedStake) throws MinimumStakeException, NotEnoughResourcesException,
			InvalidResourceException, InvalidDelegationException, MismatchException {

			tokenHoldingBucket.withdraw(preparedStake.getResourceAddr(), preparedStake.getAmount());

			if (preparedStake.getAmount().compareTo(minimumStake) < 0) {
				throw new MinimumStakeException(minimumStake, preparedStake.getAmount());
			}
			if (!preparedStake.getDelegateKey().equals(validatorKey)) {
				throw new MismatchException("Not matching validator keys");
			}

			if (!delegateAllowed.test(preparedStake.getOwner())) {
				throw new InvalidDelegationException();
			}

			return tokenHoldingBucket;
		}
	}

	private void defineStaking(Loader os) {
		// Stake
		os.procedure(new ReadProcedure<>(
			TokenHoldingBucket.class, AllowDelegationFlag.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, d, r) -> {
				var nextState = (!d.allowsDelegation())
					? new OwnerStakePrepare(s, d)
					: new StakePrepare(s, d.getValidatorKey(), p -> true);
				return ReducerResult.incomplete(nextState);
			}
		));
		os.procedure(new ReadProcedure<>(
			OwnerStakePrepare.class, ValidatorOwnerCopy.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, d, r) -> {
				var nextState = s.readOwner(d);
				return ReducerResult.incomplete(nextState);
			}
		));
		os.procedure(new UpProcedure<>(
			StakePrepare.class, PreparedStake.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var nextState = s.withdrawTo(u);
				return ReducerResult.incomplete(nextState);
			}
		));

		// Unstake
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, StakeOwnership.class,
			d -> d.bucket().withdrawAuthorization(),
			(d, s, r, c) -> ReducerResult.incomplete(new StakeOwnershipHoldingBucket(d))
		));
		// Additional Unstake
		os.procedure(new DownProcedure<>(
			StakeOwnershipHoldingBucket.class, StakeOwnership.class,
			d -> d.bucket().withdrawAuthorization(),
			(d, s, r, c) -> {
				s.depositOwnership(d);
				return ReducerResult.incomplete(s);
			}
		));
		// Change
		os.procedure(new UpProcedure<>(
			StakeOwnershipHoldingBucket.class, StakeOwnership.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var ownership = s.withdrawOwnership(u.getAmount());
				if (!ownership.equals(u)) {
					throw new MismatchException(ownership, u);
				}
				return ReducerResult.incomplete(s);
			}
		));
		os.procedure(new UpProcedure<>(
			StakeOwnershipHoldingBucket.class, PreparedUnstakeOwnership.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var unstake = s.unstake(u.getAmount());
				if (!unstake.equals(u)) {
					throw new MismatchException(unstake, u);
				}
				return ReducerResult.incomplete(s);
			}
		));

		// Deallocate Stake Holding Bucket
		os.procedure(new EndProcedure<>(
			StakeOwnershipHoldingBucket.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, c, r) -> s.destroy()
		));
	}
}
