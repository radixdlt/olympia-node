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

import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.serialization.DeserializeException;

import java.util.Set;

/**
 * Allows for the update of the epoch, timestamp and view state.
 * Currently there is only a single system particle that should be in
 * existence.
 * TODO: use a non-radix-address path to store this system info
 */
public final class SystemConstraintScryptV1 implements ConstraintScrypt {

	public SystemConstraintScryptV1() {
		// Nothing here right now
	}


	private static final class UpdatingSystem implements ReducerState {
		private final SystemParticle sys;

		private UpdatingSystem(SystemParticle sys) {
			this.sys = sys;
		}
	}


	@Override
	public void main(Loader os) {
		os.substate(new SubstateDefinition<>(
			SystemParticle.class,
			Set.of(SubstateTypeId.SYSTEM.id()),
			(b, buf) -> {
				var epoch = buf.getLong();
				if (epoch < 0) {
					throw new DeserializeException("Epoch is less than 0");
				}
				var view = buf.getLong();
				if (view < 0) {
					throw new DeserializeException("View is less than 0");
				}
				var timestamp = buf.getLong();
				if (timestamp < 0) {
					throw new DeserializeException("Timestamp is less than 0");
				}
				return new SystemParticle(epoch, view, timestamp);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.SYSTEM.id());
				buf.putLong(s.getEpoch());
				buf.putLong(s.getView());
				buf.putLong(s.getTimestamp());
			},
			p -> p.getView() == 0 && p.getEpoch() == 0 && p.getTimestamp() == 0
		));

		os.procedure(new DownProcedure<>(
			SystemParticle.class, VoidReducerState.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new UpdatingSystem(d.getSubstate()))
		));

		os.procedure(new UpProcedure<>(
			UpdatingSystem.class, SystemParticle.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var curState = s.sys;
				if (curState.getEpoch() == u.getEpoch()) {
					if (curState.getView() >= u.getView()) {
						throw new ProcedureException("Next view must be greater than previous.");
					}
				} else if (curState.getEpoch() + 1 != u.getEpoch()) {
					throw new ProcedureException("Bad next epoch");
				} else if (u.getView() != 0) {
					throw new ProcedureException("Change of epochs must start with view 0.");
				}

				return ReducerResult.complete();
			}
		));
	}
}
