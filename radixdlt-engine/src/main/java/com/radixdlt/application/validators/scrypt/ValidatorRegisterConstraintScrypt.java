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
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DeserializeException;

import java.util.OptionalLong;

public class ValidatorRegisterConstraintScrypt implements ConstraintScrypt {
	private static class UpdatingRegistered implements ReducerState {
		private final ECPublicKey validatorKey;
		private final EpochData epochData;

		UpdatingRegistered(ECPublicKey validatorKey, EpochData epochData) {
			this.validatorKey = validatorKey;
			this.epochData = epochData;
		}

		void update(ValidatorRegisteredCopy update) throws ProcedureException {
			if (!update.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Cannot update validator");
			}

			var expectedEpoch = epochData.getEpoch() + 1;
			if (update.getEpochUpdate().orElseThrow() != expectedEpoch) {
				throw new ProcedureException("Expected epoch to be " + expectedEpoch + " but is " + update.getEpochUpdate());
			}
		}
	}

	private static class UpdatingRegisteredNeedToReadEpoch implements ReducerState {
		private final ECPublicKey validatorKey;

		UpdatingRegisteredNeedToReadEpoch(ECPublicKey validatorKey) {
			this.validatorKey = validatorKey;
		}

		ReducerState readEpoch(EpochData epochData) {
			return new UpdatingRegistered(validatorKey, epochData);
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(new SubstateDefinition<>(
			ValidatorRegisteredCopy.class,
			SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var epochUpdate = REFieldSerialization.deserializeOptionalNonNegativeLong(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = REFieldSerialization.deserializeBoolean(buf);
				return new ValidatorRegisteredCopy(epochUpdate, key, flag);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeOptionalLong(buf, s.getEpochUpdate());
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.isRegistered() ? 1 : 0));
			},
			s -> s.getEpochUpdate().isEmpty() && !s.isRegistered()
		));

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorRegisteredCopy.class,
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
				return ReducerResult.incomplete(new UpdatingRegisteredNeedToReadEpoch(d.getSubstate().getValidatorKey()));
			}
		));

		os.procedure(new ReadProcedure<>(
			UpdatingRegisteredNeedToReadEpoch.class, EpochData.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> ReducerResult.incomplete(s.readEpoch(u))
		));

		os.procedure(new UpProcedure<>(
			UpdatingRegistered.class, ValidatorRegisteredCopy.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
