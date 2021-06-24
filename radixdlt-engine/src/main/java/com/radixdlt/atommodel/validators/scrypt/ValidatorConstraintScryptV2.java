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
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.atommodel.validators.state.RakeCopy;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

import java.util.Objects;
import java.util.Set;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;
import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MIN;

public class ValidatorConstraintScryptV2 implements ConstraintScrypt {
	public static final long RAKE_INCREASE_DEBOUNCE_EPOCH_LENGTH = 2;
	public static final int MAX_RAKE_INCREASE = 10 * PreparedRakeUpdate.RAKE_PERCENTAGE_GRANULARITY; // 10%

	private static class UpdatingValidatorInfo implements ReducerState {
		private final ValidatorParticle prevState;

		private UpdatingValidatorInfo(ValidatorParticle prevState) {
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

		void update(PreparedValidatorUpdate update) throws ProcedureException {
			if (!update.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Invalid key update");
			}
		}
	}

	private static class UpdatingRake implements ReducerState {
		private final RakeCopy rakeCopy;

		private UpdatingRake(RakeCopy rakeCopy) {
			this.rakeCopy = rakeCopy;
		}

		void update(ReadableAddrs r, PreparedRakeUpdate update) throws ProcedureException {
			if (!Objects.equals(rakeCopy.getValidatorKey(), update.getValidatorKey())) {
				throw new ProcedureException("Must update same key");
			}

			var rakeIncrease = update.getNextRakePercentage() - rakeCopy.getCurRakePercentage();
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
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					buf.put((byte) (s.isRegisteredForNextEpoch() ? 1 : 0)); // isRegistered
					REFieldSerialization.serializeString(buf, s.getName());
					REFieldSerialization.serializeString(buf, s.getUrl());
				},
				p -> !p.isRegisteredForNextEpoch() && p.getUrl().isEmpty() && p.getName().isEmpty()
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorParticle.class,
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
				return ReducerResult.incomplete(new UpdatingValidatorInfo(d.getSubstate()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorInfo.class, ValidatorParticle.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!Objects.equals(s.prevState.getValidatorKey(), u.getValidatorKey())) {
					throw new ProcedureException(String.format(
						"validator addresses do not match: %s != %s",
						s.prevState.getValidatorKey(), u.getValidatorKey()
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
			RakeCopy.class,
			Set.of(SubstateTypeId.RAKE_COPY.id()),
			(b, buf) -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var curRakePercentage = REFieldSerialization.deserializeInt(buf);
				return new RakeCopy(key, curRakePercentage);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.RAKE_COPY.id());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.putInt(s.getCurRakePercentage());
			},
			s -> s.getCurRakePercentage() == RAKE_MAX
		));

		os.substate(new SubstateDefinition<>(
			PreparedRakeUpdate.class,
			Set.of(SubstateTypeId.PREPARED_RAKE_UPDATE.id()),
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

				return new PreparedRakeUpdate(epoch, validatorKey, curRakePercentage, nextRakePercentage);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.PREPARED_RAKE_UPDATE.id());
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
				return ReducerResult.incomplete(new UpdatingRake(d.getSubstate().getCurrentConfig()));
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, RakeCopy.class,
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
				return ReducerResult.incomplete(new UpdatingRake(d.getSubstate()));
			}
		));
		os.procedure(new UpProcedure<>(
			UpdatingRake.class, PreparedRakeUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(r, u);
				return ReducerResult.complete();
			}
		));

		os.substate(new SubstateDefinition<>(
			AllowDelegationFlag.class,
			Set.of(SubstateTypeId.ALLOW_DELEGATION_FLAG.id()),
			(b, buf) -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = buf.get();
				if (!(flag == 0 || flag == 1)) {
					throw new DeserializeException("Invalid flag");
				}
				return new AllowDelegationFlag(key, flag == 1);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.ALLOW_DELEGATION_FLAG.id());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.allowsDelegation() ? 1 : 0));
			},
			s -> s.allowsDelegation() // TODO: for mainnet default to false
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
			Set.of(SubstateTypeId.NULL_VALIDATOR_UPDATE.id()),
			(b, buf) -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var owner = REFieldSerialization.deserializeREAddr(buf);
				if (!owner.isAccount()) {
					throw new DeserializeException("Address is not an account: " + owner);
				}
				return new ValidatorOwnerCopy(key, owner);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.NULL_VALIDATOR_UPDATE.id());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwner());
			},
			s -> REAddr.ofPubKeyAccount(s.getValidatorKey()).equals(s.getOwner())
		));

		os.substate(new SubstateDefinition<>(
			PreparedValidatorUpdate.class,
			Set.of(SubstateTypeId.PREPARED_VALIDATOR_UPDATE.id()),
			(b, buf) -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var ownerAddr = REFieldSerialization.deserializeREAddr(buf);
				if (!ownerAddr.isAccount()) {
					throw new DeserializeException("Owner address must be an account");
				}

				return new PreparedValidatorUpdate(key, ownerAddr);
			},
			(s, buf) -> {
				buf.put(SubstateTypeId.PREPARED_VALIDATOR_UPDATE.id());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwnerAddress());
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, PreparedValidatorUpdate.class,
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
			UpdatingValidator.class, PreparedValidatorUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
