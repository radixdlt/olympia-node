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

package com.radixdlt.application.validators.scrypt;

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.validators.state.PreparedRakeUpdate;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReadIndexProcedure;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;

import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MAX;
import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MIN;

public final class ValidatorUpdateRakeConstraintScrypt implements ConstraintScrypt {
	public static final int MAX_RAKE_INCREASE = 10 * PreparedRakeUpdate.RAKE_PERCENTAGE_GRANULARITY; // 10%

	private final long rakeIncreaseDebounceEpochLength;

	public ValidatorUpdateRakeConstraintScrypt(long rakeIncreaseDebounceEpochLength) {
		this.rakeIncreaseDebounceEpochLength = rakeIncreaseDebounceEpochLength;
	}

	private class UpdatingRakeReady implements ReducerState {
		private final EpochData epochData;
		private final ValidatorStakeData stakeData;

		UpdatingRakeReady(EpochData epochData, ValidatorStakeData stakeData) {
			this.epochData = epochData;
			this.stakeData = stakeData;
		}

		void update(PreparedRakeUpdate update) throws ProcedureException {
			if (!Objects.equals(stakeData.getValidatorKey(), update.getValidatorKey())) {
				throw new ProcedureException("Must update same key");
			}

			var rakeIncrease = update.getNextRakePercentage() - stakeData.getRakePercentage();
			if (rakeIncrease > MAX_RAKE_INCREASE) {
				throw new ProcedureException("Max rake increase is " + MAX_RAKE_INCREASE + " but trying to increase " + rakeIncrease);
			}

			if (rakeIncrease > 0) {
				var expectedEpoch = epochData.getEpoch() + rakeIncreaseDebounceEpochLength;
				if (update.getEpoch() < expectedEpoch) {
					throw new ProcedureException("Increasing rake requires epoch delay to " + expectedEpoch + " but was " + update.getEpoch());
				}
			} else {
				var expectedEpoch = epochData.getEpoch() + 1;
				if (update.getEpoch() != expectedEpoch) {
					throw new ProcedureException("Decreasing rake requires epoch delay to " + expectedEpoch + " but was " + update.getEpoch());
				}
			}
		}
	}

	private class UpdatingRakeNeedToReadCurrentRake implements ReducerState {
		private final ECPublicKey validatorKey;

		UpdatingRakeNeedToReadCurrentRake(ECPublicKey validatorKey) {
			this.validatorKey = validatorKey;
		}

		public ReducerState readValidatorStakeState(ValidatorStakeData validatorStakeData) throws ProcedureException {
			if (!validatorStakeData.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Invalid key update");
			}

			return new UpdatingRakeNeedToReadEpoch(validatorStakeData);
		}
	}

	private class UpdatingRakeNeedToReadEpoch implements ReducerState {
		private final ValidatorStakeData validatorStakeData;

		private UpdatingRakeNeedToReadEpoch(ValidatorStakeData validatorStakeData) {
			this.validatorStakeData = validatorStakeData;
		}

		ReducerState readEpoch(EpochData epochData) {
			return new UpdatingRakeReady(epochData, validatorStakeData);
		}
	}


	@Override
	public void main(Loader os) {
		os.substate(new SubstateDefinition<>(
			ValidatorRakeCopy.class,
			SubstateTypeId.VALIDATOR_RAKE_COPY.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var curRakePercentage = REFieldSerialization.deserializeInt(buf);
				return new ValidatorRakeCopy(key, curRakePercentage);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.putInt(s.getCurRakePercentage());
			},
			s -> s.getCurRakePercentage() == RAKE_MAX
		));

		os.substate(new SubstateDefinition<>(
			PreparedRakeUpdate.class,
			SubstateTypeId.PREPARED_RAKE_UPDATE.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
				var validatorKey = REFieldSerialization.deserializeKey(buf);
				var nextRakePercentage = REFieldSerialization.deserializeInt(buf);
				if (nextRakePercentage < RAKE_MIN || nextRakePercentage > RAKE_MAX) {
					throw new DeserializeException("Invalid rake percentage " + nextRakePercentage);
				}

				return new PreparedRakeUpdate(epoch, validatorKey, nextRakePercentage);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				buf.putLong(s.getEpoch());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.putInt(s.getNextRakePercentage());
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, PreparedRakeUpdate.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getSubstate().getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new UpdatingRakeNeedToReadCurrentRake(d.getSubstate().getValidatorKey()));
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorRakeCopy.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getSubstate().getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new UpdatingRakeNeedToReadCurrentRake(d.getSubstate().getValidatorKey()));
			}
		));
		os.procedure(new ReadProcedure<>(
			UpdatingRakeNeedToReadEpoch.class, EpochData.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> ReducerResult.incomplete(s.readEpoch(u))
		));
		os.procedure(new ReadProcedure<>(
			UpdatingRakeNeedToReadCurrentRake.class, ValidatorStakeData.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> ReducerResult.incomplete(s.readValidatorStakeState(u))
		));

		os.procedure(new UpProcedure<>(
			UpdatingRakeReady.class, PreparedRakeUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));

	}
}
