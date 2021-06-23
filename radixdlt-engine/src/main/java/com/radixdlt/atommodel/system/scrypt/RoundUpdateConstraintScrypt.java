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

import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;

public class RoundUpdateConstraintScrypt implements ConstraintScrypt {
	private static class StartValidatorBFTUpdate implements ReducerState {
		private final long view;
		private TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);

		public StartValidatorBFTUpdate(long view) {
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
			return new UpdatingValidatorBFTData(view, validatorsToUpdate);
		}
	}

	private static class UpdatingValidatorBFTData implements ReducerState {
		private long expectedNextView;
		private TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate;

		UpdatingValidatorBFTData(long view, TreeMap<ECPublicKey, ValidatorBFTData> validatorsToUpdate) {
			this.expectedNextView = view;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		// TODO: Need to catch overflow attacks
		// TODO: Verify doesnt go above max view
		private void incrementViews(long count) {
			this.expectedNextView += count;
		}

		public void update(ValidatorBFTData next) throws ProcedureException {
			var first = validatorsToUpdate.firstKey();
			if (!next.validatorKey().equals(first)) {
				throw new ProcedureException("Invalid key for validator bft data update");
			}
			var old = validatorsToUpdate.remove(first);
			if (old.proposalsCompleted() > next.proposalsCompleted()
				|| old.proposalsMissed() > next.proposalsMissed()) {
				throw new ProcedureException("Invalid data for validator bft data update");
			}

			var additionalProposalsCompleted = next.proposalsCompleted() - old.proposalsCompleted();
			var additionalProposalsMissed = next.proposalsMissed() - old.proposalsMissed();

			incrementViews(additionalProposalsCompleted);
			incrementViews(additionalProposalsMissed);
		}

		public void update(RoundData next) throws ProcedureException {
			if (this.expectedNextView != next.getView()) {
				throw new ProcedureException("Expected view " + this.expectedNextView + " but was " + next.getView());
			}
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				ValidatorBFTData.class,
				Set.of(SubstateTypeId.VALIDATOR_EPOCH_DATA.id()),
				(b, buf) -> {
					var key = REFieldSerialization.deserializeKey(buf);
					var proposalsCompleted = REFieldSerialization.deserializeNonNegativeLong(buf);
					var proposalsMissed = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new ValidatorBFTData(key, proposalsCompleted, proposalsMissed);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.VALIDATOR_EPOCH_DATA.id());
					REFieldSerialization.serializeKey(buf, s.validatorKey());
					buf.putLong(s.proposalsCompleted());
					buf.putLong(s.proposalsMissed());
				}
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, RoundData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new RoundClosed(d.getSubstate()))
		));

		os.procedure(new DownProcedure<>(
			RoundClosed.class, ValidatorBFTData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> {
				var view = s.getClosedRound().getView();
				var next = new StartValidatorBFTUpdate(view);
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
				next.update(u);
				return ReducerResult.incomplete(next);
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorBFTData.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.incomplete(s);
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorBFTData.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
