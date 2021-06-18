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

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;

import java.util.Set;

public class StakingConstraintScryptV3 implements ConstraintScrypt {

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				PreparedStake.class,
				Set.of(SubstateTypeId.PREPARED_STAKE.id()),
				(b, buf) -> {
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new PreparedStake(amount, owner, delegate);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.PREPARED_STAKE.id());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		os.substate(
			new SubstateDefinition<>(
				PreparedUnstakeOwnership.class,
				Set.of(SubstateTypeId.PREPARED_UNSTAKE.id()),
				(b, buf) -> {
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new PreparedUnstakeOwnership(delegate, owner, amount);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.PREPARED_UNSTAKE.id());
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		defineStaking(os);
	}

	private void defineStaking(Loader os) {
		// Stake
		os.procedure(new UpProcedure<>(
			TokenHoldingBucket.class, PreparedStake.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getAmount().compareTo(ValidatorStakeData.MINIMUM_STAKE) < 0) {
					throw new ProcedureException(
						"Minimum amount to stake must be >= " + ValidatorStakeData.MINIMUM_STAKE
							+ " but trying to stake " + u.getAmount()
					);
				}

				var resourceAddr = u.bucket().resourceAddr();
				var nextState = s.withdraw(resourceAddr, u.getAmount());
				return ReducerResult.incomplete(nextState);
			}
		));

		// Unstake
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, StakeOwnership.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> ReducerResult.incomplete(new StakeOwnershipHoldingBucket(d.getSubstate()))
		));
		// Additional Unstake
		os.procedure(new DownProcedure<>(
			StakeOwnershipHoldingBucket.class, StakeOwnership.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> ReducerResult.incomplete(s.depositOwnership(d.getSubstate()))
		));
		// Change
		os.procedure(new UpProcedure<>(
			StakeOwnershipHoldingBucket.class, StakeOwnership.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.withdrawOwnership(u))
		));
		os.procedure(new UpProcedure<>(
			StakeOwnershipHoldingBucket.class, PreparedUnstakeOwnership.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.unstake(u))
		));

		// Deallocate Stake Holding Bucket
		os.procedure(new EndProcedure<>(
			StakeOwnershipHoldingBucket.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			StakeOwnershipHoldingBucket::destroy
		));
	}
}
