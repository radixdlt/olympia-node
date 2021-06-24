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

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.ValidatorBFTData;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.KeyComparator;

import java.util.TreeMap;

public class RoundUpdateConstraintScrypt implements ConstraintScrypt {
	private final long maxRounds;

	public RoundUpdateConstraintScrypt(long maxRounds) {
		this.maxRounds = maxRounds;
	}

	private static class StartValidatorBFTUpdate implements ReducerState {
		private final long maxRounds;
		private final long view;
		private TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate = new TreeMap<>(KeyComparator.instance());

		StartValidatorBFTUpdate(long maxRounds, long view) {
			this.maxRounds = maxRounds;
			this.view = view;
		}

		public ReducerState beginUpdate(ValidatorBFTData validatorBFTData) throws ProcedureException {
			if (validatorsToUpdate.containsKey(validatorBFTData.validatorKey())) {
				throw new ProcedureException("Validator already started to update.");
			}

			validatorsToUpdate.put(validatorBFTData.validatorKey(), validatorBFTData);
			return this;
		}

		public UpdatingValidatorBFTData exit() {
			return new UpdatingValidatorBFTData(maxRounds, view, validatorsToUpdate);
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				RoundData.class,
				SubstateTypeId.ROUND_DATA.id(),
				buf -> {
					var view = REFieldSerialization.deserializeNonNegativeLong(buf);
					var timestamp = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new RoundData(view, timestamp);
				},
				(s, buf) -> {
					buf.putLong(s.getView());
					buf.putLong(s.getTimestamp());
				},
				p -> p.getView() == 0 && p.getTimestamp() == 0
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ValidatorBFTData.class,
				SubstateTypeId.VALIDATOR_BFT_DATA.id(),
				buf -> {
					var key = REFieldSerialization.deserializeKey(buf);
					var proposalsCompleted = REFieldSerialization.deserializeNonNegativeLong(buf);
					var proposalsMissed = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new ValidatorBFTData(key, proposalsCompleted, proposalsMissed);
				},
				(s, buf) -> {
					REFieldSerialization.serializeKey(buf, s.validatorKey());
					buf.putLong(s.proposalsCompleted());
					buf.putLong(s.proposalsMissed());
				}
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, RoundData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new EndPrevRound(d.getSubstate()))
		));

		os.procedure(new DownProcedure<>(
			EndPrevRound.class, ValidatorBFTData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> {
				var view = s.getClosedRound().getView();
				var next = new StartValidatorBFTUpdate(maxRounds, view);
				next.beginUpdate(d.getSubstate());
				return ReducerResult.incomplete(next);
			}
		));

		os.procedure(new DownProcedure<>(
			StartValidatorBFTUpdate.class, ValidatorBFTData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(s.beginUpdate(d.getSubstate()))
		));

		os.procedure(new UpProcedure<>(
			StartValidatorBFTUpdate.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var next = s.exit();
				return ReducerResult.incomplete(next.update(u));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorBFTData.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.update(u))
		));

		os.procedure(new UpProcedure<>(
			StartNextRound.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
