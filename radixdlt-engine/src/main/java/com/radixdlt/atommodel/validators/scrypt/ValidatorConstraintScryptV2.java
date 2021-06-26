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
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.PreparedOwnerUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorRakeCopy;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorMetaData;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;
import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MIN;

public class ValidatorConstraintScryptV2 implements ConstraintScrypt {
	public static final int MAX_RAKE_INCREASE = 10 * PreparedRakeUpdate.RAKE_PERCENTAGE_GRANULARITY; // 10%
	private final long rakeIncreaseDebounceEpochLength;

	public ValidatorConstraintScryptV2(long rakeIncreaseDebounceEpochLength) {
		this.rakeIncreaseDebounceEpochLength = rakeIncreaseDebounceEpochLength;
	}

	private static class UpdatingValidatorInfo implements ReducerState {
		private final ValidatorMetaData prevState;

		private UpdatingValidatorInfo(ValidatorMetaData prevState) {
			this.prevState = prevState;
		}
	}

	private static class UpdatingDelegationFlag implements ReducerState {
		private final AllowDelegationFlag current;

		private UpdatingDelegationFlag(AllowDelegationFlag current) {
			this.current = current;
		}

		void update(AllowDelegationFlag next) throws ProcedureException {
			if (!current.getValidatorKey().equals(next.getValidatorKey())) {
				throw new ProcedureException("Invalid key update");
			}
			if (current.allowsDelegation() == next.allowsDelegation()) {
				throw new ProcedureException("Already set.");
			}
		}
	}

	private static class UpdatingValidator implements ReducerState {
		private final ECPublicKey validatorKey;

		UpdatingValidator(ECPublicKey validatorKey) {
			this.validatorKey = validatorKey;
		}

		void update(PreparedOwnerUpdate update) throws ProcedureException {
			if (!update.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Invalid key update");
			}
		}
	}

	private class UpdatingRakeReady implements ReducerState {
		private final ValidatorRakeCopy rakeCopy;
		private final EpochData epochData;

		UpdatingRakeReady(ValidatorRakeCopy rakeCopy, EpochData epochData) {
			this.rakeCopy = rakeCopy;
			this.epochData = epochData;
		}

		void update(PreparedRakeUpdate update) throws ProcedureException {
			if (!Objects.equals(rakeCopy.getValidatorKey(), update.getValidatorKey())) {
				throw new ProcedureException("Must update same key");
			}

			var rakeIncrease = update.getNextRakePercentage() - rakeCopy.getCurRakePercentage();
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

	private class UpdatingRakeNeedToReadEpoch implements ReducerState {
		private final ValidatorRakeCopy rakeCopy;

		private UpdatingRakeNeedToReadEpoch(ValidatorRakeCopy rakeCopy) {
			this.rakeCopy = rakeCopy;
		}

		ReducerState readEpoch(EpochData epochData) {
			return new UpdatingRakeReady(rakeCopy, epochData);
		}
	}



	@Override
	public void main(Loader os) {

		os.substate(
			new SubstateDefinition<>(
				ValidatorMetaData.class,
				SubstateTypeId.VALIDATOR_META_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var key = REFieldSerialization.deserializeKey(buf);
					var name = REFieldSerialization.deserializeString(buf);
					var url = REFieldSerialization.deserializeUrl(buf);
					return new ValidatorMetaData(key, name, url);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getKey());
					REFieldSerialization.serializeString(buf, s.getName());
					REFieldSerialization.serializeString(buf, s.getUrl());
				},
				p -> p.getUrl().isEmpty() && p.getName().isEmpty()
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorMetaData.class,
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
			UpdatingValidatorInfo.class, ValidatorMetaData.class,
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

		registerRakeUpdates(os);
		registerValidatorOwnerUpdates(os);
	}

	public void registerRakeUpdates(Loader os) {
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
				var curRakePercentage = REFieldSerialization.deserializeInt(buf);
				if (curRakePercentage < RAKE_MIN || curRakePercentage > RAKE_MAX) {
					throw new DeserializeException("Invalid cur rake percentage " + curRakePercentage);
				}
				var nextRakePercentage = REFieldSerialization.deserializeInt(buf);
				if (nextRakePercentage < RAKE_MIN || nextRakePercentage > RAKE_MAX) {
					throw new DeserializeException("Invalid rake percentage " + nextRakePercentage);
				}

				return new PreparedRakeUpdate(epoch, validatorKey, curRakePercentage, nextRakePercentage);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				buf.putLong(s.getEpoch());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.putInt(s.getCurRakePercentage());
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
				return ReducerResult.incomplete(new UpdatingRakeNeedToReadEpoch(d.getSubstate().getCurrentConfig()));
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
				return ReducerResult.incomplete(new UpdatingRakeNeedToReadEpoch(d.getSubstate()));
			}
		));
		os.procedure(new ReadProcedure<>(
			UpdatingRakeNeedToReadEpoch.class, EpochData.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> ReducerResult.incomplete(s.readEpoch(u))
		));

		os.procedure(new UpProcedure<>(
			UpdatingRakeReady.class, PreparedRakeUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));

		os.substate(new SubstateDefinition<>(
			AllowDelegationFlag.class,
			SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = REFieldSerialization.deserializeBoolean(buf);
				return new AllowDelegationFlag(key, flag);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.allowsDelegation() ? 1 : 0));
			},
			s -> !s.allowsDelegation()
		));

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, AllowDelegationFlag.class,
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
				return ReducerResult.incomplete(new UpdatingDelegationFlag(d.getSubstate()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingDelegationFlag.class, AllowDelegationFlag.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}

	public void registerValidatorOwnerUpdates(Loader os) {
		os.substate(new SubstateDefinition<>(
			ValidatorOwnerCopy.class,
			SubstateTypeId.VALIDATOR_OWNER_COPY.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var owner = REFieldSerialization.deserializeREAddr(buf);
				if (!owner.isAccount()) {
					throw new DeserializeException("Address is not an account: " + owner);
				}
				return new ValidatorOwnerCopy(key, owner);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwner());
			},
			s -> REAddr.ofPubKeyAccount(s.getValidatorKey()).equals(s.getOwner())
		));

		os.substate(new SubstateDefinition<>(
			PreparedOwnerUpdate.class,
			SubstateTypeId.PREPARED_VALIDATOR_OWNER_UPDATE.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var ownerAddr = REFieldSerialization.deserializeREAddr(buf);
				if (!ownerAddr.isAccount()) {
					throw new DeserializeException("Owner address must be an account");
				}
				return new PreparedOwnerUpdate(key, ownerAddr);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwnerAddress());
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, PreparedOwnerUpdate.class,
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
				return ReducerResult.incomplete(new UpdatingValidator(d.getSubstate().getValidatorKey()));
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorOwnerCopy.class,
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
				return ReducerResult.incomplete(new UpdatingValidator(d.getSubstate().getValidatorKey()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidator.class, PreparedOwnerUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
