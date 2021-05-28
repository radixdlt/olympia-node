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

package com.radixdlt.atommodel.system.scrypt;

import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;

// TODO: Remove for mainnet
public class SystemV1ToV2TransitionConstraintScrypt implements ConstraintScrypt {

	private static final class TransitionToV2Round implements ReducerState {
		private final SystemParticle sys;

		private TransitionToV2Round(SystemParticle sys) {
			this.sys = sys;
		}
	}

	private static final class CreateValidatorEpochData implements ReducerState {
	}

	private static final class TransitionToV2Epoch implements ReducerState {
		private final SystemParticle sys;

		private TransitionToV2Epoch(SystemParticle sys) {
			this.sys = sys;
		}
	}

	@Override
	public void main(SysCalls os) {
		os.createDownProcedure(new DownProcedure<>(
			SystemParticle.class, SystemConstraintScryptV2.CreatingNextValidatorSet.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new TransitionToV2Epoch(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			TransitionToV2Epoch.class, EpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				var curState = s.sys;
				if (curState.getEpoch() + 1 != u.getEpoch()) {
					throw new ProcedureException("Must increment epoch");
				}

				return ReducerResult.incomplete(new SystemConstraintScryptV2.UpdatingEpochRound());
			}
		));

		os.createDownProcedure(new DownProcedure<>(
			SystemParticle.class, SystemConstraintScryptV2.UpdateValidatorEpochData.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new TransitionToV2Round(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			TransitionToV2Round.class, SystemParticle.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				if (!s.sys.equals(u)) {
					throw new ProcedureException("Transition system state must be equal.");
				}
				return ReducerResult.incomplete(new CreateValidatorEpochData());
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			CreateValidatorEpochData.class, ValidatorEpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				if (u.proposalsCompleted() != 0) {
					throw new ProcedureException("Proposals must start at 0 proposals but was " + u.proposalsCompleted());
				}
				return ReducerResult.complete();
			}
		));
	}
}
