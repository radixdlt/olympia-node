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

package com.radixdlt.atommodel.validators.scrypt;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.validators.state.ValidatorConfigCopy;
import com.radixdlt.atommodel.validators.state.PreparedValidatorConfigUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReadableAddrs;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;
import java.util.Set;

import static com.radixdlt.atommodel.validators.state.PreparedValidatorConfigUpdate.RAKE_MAX;
import static com.radixdlt.atommodel.validators.state.PreparedValidatorConfigUpdate.RAKE_MIN;

public class ValidatorConstraintScryptV2 implements ConstraintScrypt {
	public static final long RAKE_INCREASE_DEBOUNCE_EPOCH_LENGTH = 2;
	public static final int MAX_RAKE_INCREASE = 10 * PreparedValidatorConfigUpdate.RAKE_PERCENTAGE_GRANULARITY; // 10%

	private static class UpdatingValidatorInfo implements ReducerState {
		private final ValidatorParticle prevState;

		private UpdatingValidatorInfo(ValidatorParticle prevState) {
			this.prevState = prevState;
		}
	}

	private static class UpdatingValidator implements ReducerState {
		private final ValidatorConfigCopy validatorConfigCopy;

		private UpdatingValidator(ValidatorConfigCopy validatorConfigCopy) {
			this.validatorConfigCopy = validatorConfigCopy;
		}

		void update(ReadableAddrs r, PreparedValidatorConfigUpdate update) throws ProcedureException {
			if (!Objects.equals(validatorConfigCopy.getValidatorKey(), update.getValidatorKey())) {
				throw new ProcedureException("Must update same key");
			}

			var rakeIncrease = update.getNextRakePercentage() - validatorConfigCopy.getCurRakePercentage();
			if (rakeIncrease > MAX_RAKE_INCREASE) {
				throw new ProcedureException("Max rake increase is " + MAX_RAKE_INCREASE + " but trying to increase " + rakeIncrease);
			}

			var system = (HasEpochData) r.loadAddr(REAddr.ofSystem()).orElseThrow();
			if (rakeIncrease > 0) {
				var expectedEpoch = system.getEpoch() + RAKE_INCREASE_DEBOUNCE_EPOCH_LENGTH;
				if (update.getEpoch() != expectedEpoch) {
					throw new ProcedureException("Increasing rake requires epoch delay to " + expectedEpoch + " but was " + update.getEpoch());
				}
			} else {
				var expectedEpoch = system.getEpoch() + 1;
				if (update.getEpoch() != expectedEpoch) {
					throw new ProcedureException("Decreasing rake requires epoch delay to " + expectedEpoch + " but was " + update.getEpoch());
				}
			}
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				ValidatorParticle.class,
				Set.of(SubstateTypeId.VALIDATOR.id()),
				(b, buf) -> {
					var key = REFieldSerialization.deserializeKey(buf);
					var isRegistered = buf.get() != 0; // isRegistered
					var name = REFieldSerialization.deserializeString(buf);
					var url = REFieldSerialization.deserializeUrl(buf);
					return new ValidatorParticle(key, isRegistered, name, url);

				},
				(s, buf) -> {
					buf.put(SubstateTypeId.VALIDATOR.id());
					REFieldSerialization.serializeKey(buf, s.getKey());
					buf.put((byte) (s.isRegisteredForNextEpoch() ? 1 : 0)); // isRegistered
					REFieldSerialization.serializeString(buf, s.getName());
					REFieldSerialization.serializeString(buf, s.getUrl());
				},
				p -> !p.isRegisteredForNextEpoch() && p.getUrl().isEmpty() && p.getName().isEmpty()
			)
		);

		os.procedure(new DownProcedure<>(
			ValidatorParticle.class, VoidReducerState.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getSubstate().getKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new UpdatingValidatorInfo(d.getSubstate()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorInfo.class, ValidatorParticle.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!Objects.equals(s.prevState.getKey(), u.getKey())) {
					throw new ProcedureException(String.format(
						"validator addresses do not match: %s != %s",
						s.prevState.getKey(), u.getKey()
					));
				}
				return ReducerResult.complete();
			}
		));

		os.substate(new SubstateDefinition<>(
			ValidatorConfigCopy.class,
			Set.of(SubstateTypeId.VALIDATOR_NO_UPDATE.id()),
			(b, buf) -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var curRakePercentage = REFieldSerialization.deserializeInt(buf);
				return new ValidatorConfigCopy(key, curRakePercentage);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.VALIDATOR_NO_UPDATE.id());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.putInt(s.getCurRakePercentage());
			},
			s -> s.getCurRakePercentage() == 0
		));

		os.substate(
			new SubstateDefinition<>(
				PreparedValidatorConfigUpdate.class,
				Set.of(SubstateTypeId.PREPARED_VALIDATOR_UPDATE.id()),
				(b, buf) -> {
					var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
					var validatorKey = REFieldSerialization.deserializeKey(buf);
					var curRakePercentage = REFieldSerialization.deserializeInt(buf);
					if (curRakePercentage < RAKE_MIN || curRakePercentage > RAKE_MAX) {
						throw new DeserializeException("Invalid cur rake percentage " + curRakePercentage);
					}
					var nextRakePercentage = REFieldSerialization.deserializeInt(buf);
					if (nextRakePercentage < RAKE_MIN || nextRakePercentage > RAKE_MAX) {
						throw new DeserializeException("Invalid rake percentage " + nextRakePercentage);
					}

					return new PreparedValidatorConfigUpdate(epoch, validatorKey, curRakePercentage, nextRakePercentage);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.PREPARED_VALIDATOR_UPDATE.id());
					buf.putLong(s.getEpoch());
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					buf.putInt(s.getCurRakePercentage());
					buf.putInt(s.getNextRakePercentage());
				}
			)
		);

		os.procedure(new DownProcedure<>(
			PreparedValidatorConfigUpdate.class, VoidReducerState.class,
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
				return ReducerResult.incomplete(new UpdatingValidator(d.getSubstate().getCurrentConfig()));
			}
		));

		os.procedure(new DownProcedure<>(
			ValidatorConfigCopy.class, VoidReducerState.class,
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
				return ReducerResult.incomplete(new UpdatingValidator(d.getSubstate()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidator.class, PreparedValidatorConfigUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(r, u);
				return ReducerResult.complete();
			}
		));

	}
}
